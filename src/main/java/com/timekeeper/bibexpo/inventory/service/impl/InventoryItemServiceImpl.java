package com.timekeeper.bibexpo.inventory.service.impl;

import com.timekeeper.bibexpo.audit.api.AuditAction;
import com.timekeeper.bibexpo.audit.api.AuditContextHolder;
import com.timekeeper.bibexpo.audit.api.AuditEntityType;
import com.timekeeper.bibexpo.audit.api.Auditable;
import com.timekeeper.bibexpo.inventory.exception.InventoryAttributeNotFoundException;
import com.timekeeper.bibexpo.inventory.exception.InventoryAttributeOptionNotFoundException;
import com.timekeeper.bibexpo.inventory.exception.InventoryItemAlreadyExistsException;
import com.timekeeper.bibexpo.inventory.exception.InventoryItemInUseException;
import com.timekeeper.bibexpo.inventory.exception.InventoryItemLinkedToGoodieException;
import com.timekeeper.bibexpo.inventory.exception.InventoryItemNotFoundException;
import com.timekeeper.bibexpo.inventory.exception.InventoryTermNotFoundException;
import com.timekeeper.bibexpo.inventory.exception.InventoryVariantAlreadyExistsException;
import com.timekeeper.bibexpo.inventory.exception.InventoryVariantInUseException;
import com.timekeeper.bibexpo.inventory.exception.InventoryVariantLimitReachedException;
import com.timekeeper.bibexpo.inventory.exception.InventoryVariantNotFoundException;
import com.timekeeper.bibexpo.inventory.model.dto.request.CreateInventoryItemRequest;
import com.timekeeper.bibexpo.inventory.model.dto.request.CreateInventoryVariantRequest;
import com.timekeeper.bibexpo.inventory.model.dto.request.ItemAttributeValueRequest;
import com.timekeeper.bibexpo.inventory.model.dto.request.UpdateInventoryItemRequest;
import com.timekeeper.bibexpo.inventory.model.dto.response.InventoryItemResponse;
import com.timekeeper.bibexpo.inventory.model.dto.response.InventoryItemSummaryResponse;
import com.timekeeper.bibexpo.inventory.model.dto.response.InventoryVariantResponse;
import com.timekeeper.bibexpo.inventory.model.dto.response.ItemAttributeValueResponse;
import com.timekeeper.bibexpo.inventory.model.entity.InventoryAttribute;
import com.timekeeper.bibexpo.inventory.model.entity.InventoryAttributeOption;
import com.timekeeper.bibexpo.inventory.model.entity.InventoryItem;
import com.timekeeper.bibexpo.inventory.model.entity.InventoryItemAttributeValue;
import com.timekeeper.bibexpo.inventory.model.entity.InventoryTerm;
import com.timekeeper.bibexpo.inventory.model.entity.InventoryVariant;
import com.timekeeper.bibexpo.inventory.model.entity.InventoryVariantAttributeValue;
import com.timekeeper.bibexpo.inventory.model.enums.AttributeType;
import com.timekeeper.bibexpo.inventory.model.enums.TermKind;
import com.timekeeper.bibexpo.inventory.repository.InventoryAttributeRepository;
import com.timekeeper.bibexpo.inventory.repository.InventoryAttributeOptionRepository;
import com.timekeeper.bibexpo.inventory.repository.InventoryItemAttributeValueRepository;
import com.timekeeper.bibexpo.inventory.repository.InventoryGoodieMappingRepository;
import com.timekeeper.bibexpo.inventory.repository.InventoryItemRepository;
import com.timekeeper.bibexpo.inventory.repository.InventoryStockRepository;
import com.timekeeper.bibexpo.inventory.repository.InventoryTermRepository;
import com.timekeeper.bibexpo.inventory.repository.InventoryVariantAttributeValueRepository;
import com.timekeeper.bibexpo.inventory.repository.InventoryVariantRepository;
import com.timekeeper.bibexpo.inventory.service.InventoryItemService;
import com.timekeeper.bibexpo.inventory.service.validator.InventoryAccessGuard;
import com.timekeeper.bibexpo.organization.api.InventoryLimits;
import com.timekeeper.bibexpo.organization.api.OrganizationDirectory;
import com.timekeeper.bibexpo.shared.error.InvalidUserDataException;
import com.timekeeper.bibexpo.storage.exception.InvalidFileException;
import com.timekeeper.bibexpo.storage.model.dto.response.PresignUploadResponse;
import com.timekeeper.bibexpo.storage.model.enums.UploadCategory;
import com.timekeeper.bibexpo.storage.service.StorageService;
import com.timekeeper.bibexpo.user.model.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryItemServiceImpl implements InventoryItemService {

    private final InventoryItemRepository itemRepository;
    private final InventoryGoodieMappingRepository goodieMappingRepository;
    private final InventoryVariantRepository variantRepository;
    private final InventoryStockRepository stockRepository;
    private final InventoryTermRepository termRepository;
    private final InventoryAttributeRepository attributeRepository;
    private final InventoryAttributeOptionRepository optionRepository;
    private final InventoryItemAttributeValueRepository itemAttributeValueRepository;
    private final InventoryVariantAttributeValueRepository variantAttributeValueRepository;
    private final OrganizationDirectory organizationDirectory;
    private final InventoryAccessGuard accessGuard;
    private final StorageService storageService;

    // A resolved attribute value ready to persist, either an optionId (SELECT) or a rawValue (any other type).
    private record ResolvedAttributeValue(Long attributeId, Long optionId, String rawValue) {
    }

    @Override
    @Transactional(readOnly = true)
    public Page<InventoryItemSummaryResponse> listItems(Long organizationId, String name, Long categoryId,
                                                        Instant createdFrom, Instant createdTo,
                                                        Pageable pageable, User currentUser) {
        accessGuard.requireOrgAccess(currentUser, organizationId);
        if (createdFrom != null && createdTo != null && createdFrom.isAfter(createdTo)) {
            throw new InvalidUserDataException("The start of the date range must come before its end.");
        }
        // Without an order the database may return the same rows in a different order on the next
        // request, so a row could land on two pages or on none. The id breaks ties on the instant.
        Pageable effective = pageable.getSort().isSorted() ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                        Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        return itemRepository.search(organizationId, blankToNull(name), categoryId, createdFrom, createdTo, effective)
                .map(InventoryItemSummaryResponse::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public InventoryItemResponse getItem(Long organizationId, Long itemId, User currentUser) {
        accessGuard.requireOrgAccess(currentUser, organizationId);
        InventoryItem item = requireItemInOrg(itemId, organizationId);
        return toItemResponse(item, variantRepository.findByItemId(itemId));
    }

    @Auditable(entityType = AuditEntityType.INVENTORY_ITEM, action = AuditAction.CREATE)
    @Override
    @Transactional
    public InventoryItemResponse createItem(Long organizationId, CreateInventoryItemRequest request, User currentUser) {
        accessGuard.requireOrgAccess(currentUser, organizationId);
        return createItemInternal(organizationId, request);
    }

    @Auditable(entityType = AuditEntityType.INVENTORY_ITEM, action = AuditAction.UPDATE)
    @Override
    @Transactional
    public InventoryItemResponse updateItem(Long organizationId, Long itemId, UpdateInventoryItemRequest request,
                                             User currentUser) {
        accessGuard.requireOrgAccess(currentUser, organizationId);
        InventoryItem item = requireItemInOrg(itemId, organizationId);

        if (request.getName() != null) {
            String name = request.getName().trim();
            if (!name.equalsIgnoreCase(item.getName())
                    && itemRepository.existsByOrganizationIdAndName(organizationId, name)) {
                throw new InventoryItemAlreadyExistsException();
            }
            item.setName(name);
        }
        if (request.getCategoryId() != null) {
            requireVisibleTerm(request.getCategoryId(), TermKind.ITEM_CATEGORY, organizationId);
            item.setCategoryId(request.getCategoryId());
        }
        if (request.getUnitId() != null) {
            requireVisibleTerm(request.getUnitId(), TermKind.UNIT, organizationId);
            item.setUnitId(request.getUnitId());
        }
        if (request.getLowStockThreshold() != null) {
            item.setLowStockThreshold(request.getLowStockThreshold());
        }
        if (request.getNote() != null) {
            item.setNote(blankToNull(request.getNote()));
        }

        InventoryItem saved = itemRepository.saveAndFlush(item);
        log.info("Updated inventory item {} for organization {}", itemId, organizationId);
        return toItemResponse(saved, variantRepository.findByItemId(itemId));
    }

    @Auditable(entityType = AuditEntityType.INVENTORY_ITEM, action = AuditAction.DELETE)
    @Override
    @Transactional
    public void deleteItem(Long organizationId, Long itemId, User currentUser) {
        accessGuard.requireOrgAccess(currentUser, organizationId);
        InventoryItem item = requireItemInOrg(itemId, organizationId);

        if (goodieMappingRepository.existsByItemId(itemId)) {
            throw new InventoryItemLinkedToGoodieException();
        }

        List<InventoryVariant> variants = variantRepository.findByItemId(itemId);
        requireNoStock(variants, InventoryItemInUseException::new);

        variants.forEach(variant -> {
            stockRepository.deleteAll(stockRepository.findByVariantId(variant.getId()));
            variantAttributeValueRepository.deleteAll(variantAttributeValueRepository.findByVariantId(variant.getId()));
            deleteImageQuietly(variant.getImageKey());
        });
        variantRepository.deleteAll(variants);
        itemAttributeValueRepository.deleteAll(itemAttributeValueRepository.findByItemId(itemId));

        AuditContextHolder.setEntityId(String.valueOf(itemId));
        AuditContextHolder.setEntityLabel(item.getName());
        AuditContextHolder.setOrganizationId(organizationId);
        itemRepository.delete(item);
        log.info("Deleted inventory item {} for organization {}", itemId, organizationId);
    }

    @Auditable(entityType = AuditEntityType.INVENTORY_ITEM, action = AuditAction.UPDATE)
    @Override
    @Transactional
    public InventoryItemResponse addVariant(Long organizationId, Long itemId, CreateInventoryVariantRequest request,
                                             User currentUser) {
        accessGuard.requireOrgAccess(currentUser, organizationId);
        InventoryItem item = requireItemInOrg(itemId, organizationId);

        List<InventoryVariant> existingVariants = variantRepository.findByItemId(itemId);
        List<ResolvedAttributeValue> resolved = resolveAttributeValues(request.getAttributeValues(), true, organizationId);
        validateConsistentWithExisting(existingVariants, resolved);
        enforceVariantLimits(organizationId, existingVariants.size() + 1, resolved.size());
        String combinationKey = computeCombinationKey(resolved);
        boolean duplicate = combinationKey == null
                ? !existingVariants.isEmpty()
                : existingVariants.stream().anyMatch(v -> combinationKey.equals(v.getCombinationKey()));
        if (duplicate) {
            throw new InventoryVariantAlreadyExistsException();
        }

        InventoryVariant variant = variantRepository.save(InventoryVariant.builder()
                .itemId(itemId).combinationKey(combinationKey).build());
        saveVariantAttributeValues(variant.getId(), resolved);

        List<InventoryVariant> variants = variantRepository.findByItemId(itemId);
        syncVariantCount(item, variants);

        log.info("Added variant {} to inventory item {} for organization {}", variant.getId(), itemId, organizationId);
        return toItemResponse(item, variants);
    }

    @Auditable(entityType = AuditEntityType.INVENTORY_ITEM, action = AuditAction.UPDATE)
    @Override
    @Transactional
    public InventoryItemResponse removeVariant(Long organizationId, Long itemId, Long variantId, User currentUser) {
        accessGuard.requireOrgAccess(currentUser, organizationId);
        InventoryItem item = requireItemInOrg(itemId, organizationId);
        InventoryVariant variant = requireVariantOfItem(variantId, itemId);

        List<InventoryVariant> variants = variantRepository.findByItemId(itemId);
        if (variants.size() <= 1) {
            throw new InvalidUserDataException("An item must keep at least one variant. Delete the item instead.");
        }
        requireNoStock(List.of(variant), InventoryVariantInUseException::new);

        stockRepository.deleteAll(stockRepository.findByVariantId(variantId));
        variantAttributeValueRepository.deleteAll(variantAttributeValueRepository.findByVariantId(variantId));
        variantRepository.delete(variant);
        deleteImageQuietly(variant.getImageKey());

        List<InventoryVariant> remaining = variantRepository.findByItemId(itemId);
        syncVariantCount(item, remaining);

        log.info("Removed variant {} from inventory item {} for organization {}", variantId, itemId, organizationId);
        return toItemResponse(item, remaining);
    }

    @Override
    @Transactional(readOnly = true)
    public PresignUploadResponse createVariantImageUploadUrl(Long organizationId, Long itemId, Long variantId,
                                                               String contentType, User currentUser) {
        accessGuard.requireOrgAccess(currentUser, organizationId);
        requireItemInOrg(itemId, organizationId);
        requireVariantOfItem(variantId, itemId);
        return storageService.createUploadUrl(UploadCategory.INVENTORY_VARIANT_IMAGE, variantId, contentType);
    }

    @Auditable(entityType = AuditEntityType.INVENTORY_ITEM, action = AuditAction.UPDATE)
    @Override
    @Transactional
    public InventoryItemResponse attachVariantImage(Long organizationId, Long itemId, Long variantId, String objectKey,
                                                      User currentUser) {
        accessGuard.requireOrgAccess(currentUser, organizationId);
        InventoryItem item = requireItemInOrg(itemId, organizationId);
        InventoryVariant variant = requireVariantOfItem(variantId, itemId);

        if (UploadCategory.INVENTORY_VARIANT_IMAGE.isForeignKeyFor(variantId, objectKey)) {
            throw new InvalidFileException("This upload does not belong to this variant.");
        }
        if (!storageService.objectExists(objectKey)) {
            throw new InvalidFileException("The uploaded file could not be found.");
        }

        String previousKey = variant.getImageKey();
        variant.setImageKey(objectKey);
        variantRepository.saveAndFlush(variant);
        if (previousKey != null && !previousKey.equals(objectKey)) {
            deleteImageQuietly(previousKey);
        }

        log.info("Attached image to variant {} on inventory item {} for organization {}", variantId, itemId, organizationId);
        return toItemResponse(item, variantRepository.findByItemId(itemId));
    }

    @Auditable(entityType = AuditEntityType.INVENTORY_ITEM, action = AuditAction.UPDATE)
    @Override
    @Transactional
    public InventoryItemResponse removeVariantImage(Long organizationId, Long itemId, Long variantId, User currentUser) {
        accessGuard.requireOrgAccess(currentUser, organizationId);
        InventoryItem item = requireItemInOrg(itemId, organizationId);
        InventoryVariant variant = requireVariantOfItem(variantId, itemId);

        String previousKey = variant.getImageKey();
        variant.setImageKey(null);
        variantRepository.saveAndFlush(variant);
        deleteImageQuietly(previousKey);

        log.info("Removed image from variant {} on inventory item {} for organization {}", variantId, itemId, organizationId);
        return toItemResponse(item, variantRepository.findByItemId(itemId));
    }

    private void deleteImageQuietly(String objectKey) {
        try {
            storageService.delete(objectKey);
        } catch (Exception e) {
            log.warn("Failed to delete object {}: {}", objectKey, e.getMessage());
        }
    }

    // ---- creation ----------------------------------------------------------------

    private InventoryItemResponse createItemInternal(Long organizationId, CreateInventoryItemRequest request) {
        requireVisibleTerm(request.getCategoryId(), TermKind.ITEM_CATEGORY, organizationId);
        requireVisibleTerm(request.getUnitId(), TermKind.UNIT, organizationId);

        String name = request.getName().trim();
        if (itemRepository.existsByOrganizationIdAndName(organizationId, name)) {
            throw new InventoryItemAlreadyExistsException();
        }

        InventoryItem item = itemRepository.save(InventoryItem.builder()
                .organizationId(organizationId)
                .name(name)
                .categoryId(request.getCategoryId())
                .unitId(request.getUnitId())
                .lowStockThreshold(request.getLowStockThreshold())
                .note(blankToNull(request.getNote()))
                .build());

        List<ResolvedAttributeValue> itemAttributes = resolveAttributeValues(request.getAttributes(), false, organizationId);
        saveItemAttributes(item.getId(), itemAttributes);

        List<InventoryVariant> variants = createVariants(item.getId(), organizationId, request.getVariants());
        syncVariantCount(item, variants);

        log.info("Created inventory item '{}' for organization {}", name, organizationId);
        return toItemResponse(item, variants);
    }

    private List<InventoryVariant> createVariants(Long itemId, Long organizationId,
                                                    List<CreateInventoryVariantRequest> rawVariants) {
        if (rawVariants == null || rawVariants.isEmpty()) {
            return List.of(variantRepository.save(InventoryVariant.builder().itemId(itemId).build()));
        }

        List<List<ResolvedAttributeValue>> resolvedPerVariant = rawVariants.stream()
                .map(v -> resolveAttributeValues(v.getAttributeValues(), true, organizationId))
                .toList();
        validateSameAttributeSet(resolvedPerVariant);
        enforceVariantLimits(organizationId, rawVariants.size(), resolvedPerVariant.get(0).size());

        List<String> combinationKeys = resolvedPerVariant.stream().map(this::computeCombinationKey).toList();
        List<String> nonNullKeys = combinationKeys.stream().filter(Objects::nonNull).toList();
        if (nonNullKeys.size() != combinationKeys.size() && combinationKeys.size() > 1) {
            throw new InvalidUserDataException("An item can hold only one variant without attribute values.");
        }
        if (nonNullKeys.stream().distinct().count() != nonNullKeys.size()) {
            throw new InventoryVariantAlreadyExistsException();
        }

        List<InventoryVariant> variants = new ArrayList<>();
        for (int i = 0; i < rawVariants.size(); i++) {
            InventoryVariant variant = variantRepository.save(InventoryVariant.builder()
                    .itemId(itemId).combinationKey(combinationKeys.get(i)).build());
            saveVariantAttributeValues(variant.getId(), resolvedPerVariant.get(i));
            variants.add(variant);
        }
        return variants;
    }

    // ---- attribute resolution ----------------------------------------------------------------

    private List<ResolvedAttributeValue> resolveAttributeValues(List<ItemAttributeValueRequest> raw,
                                                                  boolean expectVariantAttribute, Long organizationId) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<ResolvedAttributeValue> resolved = raw.stream()
                .map(req -> resolveAttributeValue(req, expectVariantAttribute, organizationId))
                .toList();
        long distinctAttributes = resolved.stream().map(ResolvedAttributeValue::attributeId).distinct().count();
        if (distinctAttributes != resolved.size()) {
            throw new InvalidUserDataException("Each attribute can only be set once.");
        }
        return resolved;
    }

    private ResolvedAttributeValue resolveAttributeValue(ItemAttributeValueRequest request, boolean expectVariantAttribute,
                                                           Long organizationId) {
        InventoryAttribute attribute = requireVisibleAttribute(request.getAttributeId(), organizationId);
        if (attribute.isVariantAttribute() != expectVariantAttribute) {
            throw new InvalidUserDataException(expectVariantAttribute
                    ? "This attribute does not define variants and belongs on the item instead."
                    : "This attribute defines variants and belongs on each variant instead.");
        }

        if (attribute.getType() == AttributeType.SELECT) {
            if (request.getOptionId() == null) {
                throw new InvalidUserDataException("Choose a value for this attribute.");
            }
            InventoryAttributeOption value = optionRepository.findById(request.getOptionId())
                    .orElseThrow(InventoryAttributeOptionNotFoundException::new);
            if (!value.getAttributeId().equals(attribute.getId())) {
                throw new InventoryAttributeOptionNotFoundException();
            }
            return new ResolvedAttributeValue(attribute.getId(), value.getId(), null);
        }

        if (request.getRawValue() == null || request.getRawValue().isBlank()) {
            throw new InvalidUserDataException("Enter a value for this attribute.");
        }
        return new ResolvedAttributeValue(attribute.getId(), null,
                normalizeRawValue(attribute.getType(), request.getRawValue().trim()));
    }

    // A declared type that nothing checks would let "maybe" into a NUMBER column, and would give
    // "True" and "true" two different combination keys for what is one variant.
    private String normalizeRawValue(AttributeType type, String rawValue) {
        if (type == AttributeType.NUMBER) {
            try {
                return new BigDecimal(rawValue).stripTrailingZeros().toPlainString();
            } catch (NumberFormatException e) {
                throw new InvalidUserDataException("Enter a number for this attribute.");
            }
        }
        if (type == AttributeType.BOOLEAN) {
            if (!"true".equalsIgnoreCase(rawValue) && !"false".equalsIgnoreCase(rawValue)) {
                throw new InvalidUserDataException("Enter true or false for this attribute.");
            }
            return rawValue.toLowerCase();
        }
        return rawValue;
    }

    private void validateSameAttributeSet(List<List<ResolvedAttributeValue>> perVariant) {
        if (perVariant.size() <= 1) {
            return;
        }
        Set<Long> firstSet = toAttributeIdSet(perVariant.get(0));
        boolean consistent = perVariant.stream().allMatch(v -> toAttributeIdSet(v).equals(firstSet));
        if (!consistent) {
            throw new InvalidUserDataException("Every variant of an item must vary by the same attributes.");
        }
    }

    // Variants created before this item had attributes are ignored — only the variants already
    // carrying attribute values constrain which attributes a newly added variant must also carry.
    private void validateConsistentWithExisting(List<InventoryVariant> existingVariants, List<ResolvedAttributeValue> resolved) {
        List<InventoryVariant> attributed = existingVariants.stream().filter(v -> v.getCombinationKey() != null).toList();
        if (attributed.isEmpty()) {
            return;
        }
        Set<Long> expected = variantAttributeValueRepository.findByVariantId(attributed.get(0).getId()).stream()
                .map(InventoryVariantAttributeValue::getAttributeId)
                .collect(Collectors.toSet());
        Set<Long> actual = toAttributeIdSet(resolved);
        if (!expected.equals(actual)) {
            throw new InvalidUserDataException("Every variant of an item must vary by the same attributes.");
        }
    }

    private Set<Long> toAttributeIdSet(List<ResolvedAttributeValue> values) {
        return values.stream().map(ResolvedAttributeValue::attributeId).collect(Collectors.toSet());
    }

    private String computeCombinationKey(List<ResolvedAttributeValue> values) {
        if (values.isEmpty()) {
            return null;
        }
        return values.stream()
                .sorted(Comparator.comparing(ResolvedAttributeValue::attributeId))
                .map(v -> v.attributeId() + ":" + (v.optionId() != null ? v.optionId() : v.rawValue()))
                .collect(Collectors.joining("|"));
    }

    private void saveItemAttributes(Long itemId, List<ResolvedAttributeValue> values) {
        values.forEach(v -> itemAttributeValueRepository.save(InventoryItemAttributeValue.builder()
                .itemId(itemId)
                .attributeId(v.attributeId())
                .optionId(v.optionId())
                .rawValue(v.rawValue())
                .build()));
    }

    private void saveVariantAttributeValues(Long variantId, List<ResolvedAttributeValue> values) {
        values.forEach(v -> variantAttributeValueRepository.save(InventoryVariantAttributeValue.builder()
                .variantId(variantId)
                .attributeId(v.attributeId())
                .optionId(v.optionId())
                .rawValue(v.rawValue())
                .build()));
    }

    private InventoryAttribute requireVisibleAttribute(Long attributeId, Long organizationId) {
        InventoryAttribute attribute = attributeRepository.findById(attributeId)
                .orElseThrow(InventoryAttributeNotFoundException::new);
        boolean visible = attribute.getOrganizationId() == null || attribute.getOrganizationId().equals(organizationId);
        if (!visible) {
            throw new InventoryAttributeNotFoundException();
        }
        return attribute;
    }

    // ---- response assembly ----------------------------------------------------------------

    // A blank note is stored as absent, so clearing one and never writing one look the same to
    // every reader; a blank search term is likewise no search at all.
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    // Set from the variants just read, never incremented, so the stored count cannot drift away
    // from the rows it counts.
    private void syncVariantCount(InventoryItem item, List<InventoryVariant> variants) {
        item.setVariantCount(variants.size());
        itemRepository.saveAndFlush(item);
    }

    private InventoryItemResponse toItemResponse(InventoryItem item, List<InventoryVariant> variants) {
        List<InventoryItemAttributeValue> itemAttributes = itemAttributeValueRepository.findByItemId(item.getId());
        List<Long> variantIds = variants.stream().map(InventoryVariant::getId).toList();
        List<InventoryVariantAttributeValue> variantAttributes = variantIds.isEmpty()
                ? List.of() : variantAttributeValueRepository.findByVariantIdIn(variantIds);

        Set<Long> attributeIds = new HashSet<>();
        itemAttributes.forEach(a -> attributeIds.add(a.getAttributeId()));
        variantAttributes.forEach(a -> attributeIds.add(a.getAttributeId()));
        Map<Long, InventoryAttribute> attributes = attributeIds.isEmpty() ? Map.of()
                : attributeRepository.findAllById(attributeIds).stream()
                        .collect(Collectors.toMap(InventoryAttribute::getId, Function.identity()));

        Set<Long> optionIds = new HashSet<>();
        itemAttributes.forEach(a -> { if (a.getOptionId() != null) optionIds.add(a.getOptionId()); });
        variantAttributes.forEach(a -> { if (a.getOptionId() != null) optionIds.add(a.getOptionId()); });
        Map<Long, InventoryAttributeOption> values = optionIds.isEmpty() ? Map.of()
                : optionRepository.findAllById(optionIds).stream()
                        .collect(Collectors.toMap(InventoryAttributeOption::getId, Function.identity()));

        List<ItemAttributeValueResponse> itemAttributeResponses = itemAttributes.stream()
                .map(a -> toAttributeValueResponse(a.getAttributeId(), a.getOptionId(), a.getRawValue(),
                        attributes, values))
                .toList();

        Map<Long, List<InventoryVariantAttributeValue>> byVariant = variantAttributes.stream()
                .collect(Collectors.groupingBy(InventoryVariantAttributeValue::getVariantId));

        List<InventoryVariantResponse> variantResponses = variants.stream()
                .map(v -> InventoryVariantResponse.fromEntity(v, byVariant.getOrDefault(v.getId(), List.of()).stream()
                        .map(a -> toAttributeValueResponse(a.getAttributeId(), a.getOptionId(), a.getRawValue(),
                                attributes, values))
                        .toList(), storageService.createDownloadUrl(v.getImageKey())))
                .toList();

        return InventoryItemResponse.fromEntity(item, variantResponses, itemAttributeResponses);
    }

    private ItemAttributeValueResponse toAttributeValueResponse(Long attributeId, Long optionId, String rawValue,
                                                                  Map<Long, InventoryAttribute> attributes,
                                                                  Map<Long, InventoryAttributeOption> values) {
        InventoryAttribute attribute = attributes.get(attributeId);
        String displayValue = optionId != null
                ? (values.containsKey(optionId) ? values.get(optionId).getValue() : null)
                : rawValue;
        return ItemAttributeValueResponse.builder()
                .attributeId(attributeId)
                .attributeName(attribute != null ? attribute.getName() : null)
                .optionId(optionId)
                .value(displayValue)
                .build();
    }

    // ---- lookups ----------------------------------------------------------------

    private InventoryItem requireItemInOrg(Long itemId, Long organizationId) {
        return itemRepository.findByIdAndOrganizationId(itemId, organizationId)
                .orElseThrow(InventoryItemNotFoundException::new);
    }

    private InventoryVariant requireVariantOfItem(Long variantId, Long itemId) {
        InventoryVariant variant = variantRepository.findById(variantId)
                .orElseThrow(InventoryVariantNotFoundException::new);
        if (!variant.getItemId().equals(itemId)) {
            throw new InventoryVariantNotFoundException();
        }
        return variant;
    }

    private void requireVisibleTerm(Long termId, TermKind kind, Long organizationId) {
        InventoryTerm term = termRepository.findById(termId)
                .orElseThrow(InventoryTermNotFoundException::new);
        boolean visible = term.getKind() == kind
                && (term.getOrganizationId() == null || term.getOrganizationId().equals(organizationId));
        if (!visible) {
            throw new InventoryTermNotFoundException();
        }
    }

    // A variant with stock anywhere would vanish without a trace, so deletion is blocked until the
    // balance is drawn down to zero; the ledger itself is never touched.
    private void requireNoStock(List<InventoryVariant> variants, Supplier<RuntimeException> onInUse) {
        boolean hasStock = variants.stream()
                .flatMap(variant -> stockRepository.findByVariantId(variant.getId()).stream())
                .anyMatch(stock -> stock.getOnHand() != null && stock.getOnHand() > 0);
        if (hasStock) {
            throw onInUse.get();
        }
    }

    // Check-then-act like the term and option caps: a double-click racing past one by a single
    // variant costs nothing worth a counter column.
    private void enforceVariantLimits(Long organizationId, int variantCount, int variantAttributeCount) {
        InventoryLimits limits = organizationDirectory.inventoryLimits(organizationId);
        if (variantCount > limits.maxVariantsPerItem()) {
            log.error("Organization {} has reached its variant limit on an inventory item", organizationId);
            throw new InventoryVariantLimitReachedException(
                    "This item already has as many variants as your plan allows.");
        }
        if (variantAttributeCount > limits.maxVariantAttributesPerItem()) {
            log.error("Organization {} has reached its variant-attribute limit on an inventory item", organizationId);
            throw new InventoryVariantLimitReachedException(
                    "This item is split by more attributes than your plan allows.");
        }
    }
}
