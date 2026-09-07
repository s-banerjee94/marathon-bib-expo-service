package com.timekeeper.bibexpo.inventory.service.impl;

import com.timekeeper.bibexpo.audit.api.AuditAction;
import com.timekeeper.bibexpo.audit.api.AuditContextHolder;
import com.timekeeper.bibexpo.audit.api.AuditEntityType;
import com.timekeeper.bibexpo.audit.api.Auditable;
import com.timekeeper.bibexpo.inventory.exception.InsufficientStockException;
import com.timekeeper.bibexpo.inventory.exception.InventoryLocationNotFoundException;
import com.timekeeper.bibexpo.inventory.exception.InventoryVariantNotFoundException;
import com.timekeeper.bibexpo.inventory.model.dto.request.AdjustStockRequest;
import com.timekeeper.bibexpo.inventory.model.dto.request.ReceiveStockRequest;
import com.timekeeper.bibexpo.inventory.model.dto.request.TransferStockRequest;
import com.timekeeper.bibexpo.inventory.model.dto.response.StockBalanceResponse;
import com.timekeeper.bibexpo.inventory.model.dto.response.StockItemResponse;
import com.timekeeper.bibexpo.inventory.model.dto.response.StockMovementResponse;
import com.timekeeper.bibexpo.inventory.model.dto.response.StockRowResponse;
import com.timekeeper.bibexpo.inventory.model.entity.InventoryAttributeOption;
import com.timekeeper.bibexpo.inventory.model.entity.InventoryItem;
import com.timekeeper.bibexpo.inventory.model.entity.InventoryLocation;
import com.timekeeper.bibexpo.inventory.model.entity.InventoryMovement;
import com.timekeeper.bibexpo.inventory.model.entity.InventoryStock;
import com.timekeeper.bibexpo.inventory.model.entity.InventoryVariant;
import com.timekeeper.bibexpo.inventory.model.entity.InventoryVariantAttributeValue;
import com.timekeeper.bibexpo.inventory.model.enums.MovementReason;
import com.timekeeper.bibexpo.inventory.model.enums.MovementType;
import com.timekeeper.bibexpo.inventory.repository.InventoryAttributeOptionRepository;
import com.timekeeper.bibexpo.inventory.repository.InventoryItemRepository;
import com.timekeeper.bibexpo.inventory.repository.InventoryLocationRepository;
import com.timekeeper.bibexpo.inventory.repository.InventoryMovementRepository;
import com.timekeeper.bibexpo.inventory.repository.InventoryStockRepository;
import com.timekeeper.bibexpo.inventory.repository.InventoryVariantAttributeValueRepository;
import com.timekeeper.bibexpo.inventory.repository.InventoryVariantRepository;
import com.timekeeper.bibexpo.inventory.service.StockService;
import com.timekeeper.bibexpo.inventory.service.validator.InventoryAccessGuard;
import com.timekeeper.bibexpo.shared.error.InvalidUserDataException;
import com.timekeeper.bibexpo.user.model.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockServiceImpl implements StockService {

    // An adjustment explains a recount. The other reasons belong to movements no adjustment posts.
    private static final Set<MovementReason> ADJUSTMENT_REASONS =
            EnumSet.of(MovementReason.DAMAGED, MovementReason.LOST, MovementReason.CORRECTION);

    private final InventoryStockRepository stockRepository;
    private final InventoryMovementRepository movementRepository;
    private final InventoryVariantRepository variantRepository;
    private final InventoryVariantAttributeValueRepository variantAttributeValueRepository;
    private final InventoryAttributeOptionRepository optionRepository;
    private final InventoryItemRepository itemRepository;
    private final InventoryLocationRepository locationRepository;
    private final InventoryAccessGuard accessGuard;

    @Override
    @Transactional(readOnly = true)
    public Page<StockItemResponse> listStock(Long organizationId, String search, Long locationId, Boolean lowOnly,
                                             Pageable pageable, User currentUser) {
        accessGuard.requireOrgAccess(currentUser, organizationId);
        String needle = blankToNull(search);
        Pageable effective = pageable.getSort().isSorted() ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by("name"));

        // The database decides which items the page holds, so the page counts describe the filtered
        // set and no item is ever split across two pages.
        Page<InventoryItem> items = itemRepository.searchWithStock(organizationId, needle, locationId,
                Boolean.TRUE.equals(lowOnly) ? Boolean.TRUE : null, effective);
        Map<Long, List<StockRowResponse>> rows = rowsFor(items.getContent(), needle, locationId);
        return items.map(item -> toStockItem(item, rows.getOrDefault(item.getId(), List.of())));
    }

    /** The balance rows behind one page of items, keyed by item, in five queries however long the page. */
    private Map<Long, List<StockRowResponse>> rowsFor(List<InventoryItem> items, String needle, Long locationId) {
        List<Long> itemIds = items.stream().map(InventoryItem::getId).toList();
        List<InventoryVariant> variants = itemIds.isEmpty() ? List.of() : variantRepository.findByItemIdIn(itemIds);
        List<Long> variantIds = variants.stream().map(InventoryVariant::getId).toList();
        List<InventoryStock> balances = variantIds.isEmpty() ? List.of()
                : stockRepository.findByVariantIdIn(variantIds).stream()
                        .filter(b -> locationId == null || locationId.equals(b.getLocationId()))
                        .toList();
        if (balances.isEmpty()) {
            return Map.of();
        }

        Map<Long, Long> itemIdByVariant = variants.stream()
                .collect(Collectors.toMap(InventoryVariant::getId, InventoryVariant::getItemId));
        Map<Long, String> locationNames = locationRepository.findAllById(balances.stream()
                        .map(InventoryStock::getLocationId).distinct().toList()).stream()
                .collect(Collectors.toMap(InventoryLocation::getId, InventoryLocation::getName));
        Map<Long, String> variantLabels = variantLabels(variantIds);

        Map<Long, List<StockRowResponse>> byItem = new HashMap<>();
        for (InventoryStock balance : balances) {
            byItem.computeIfAbsent(itemIdByVariant.get(balance.getVariantId()), k -> new ArrayList<>())
                    .add(StockRowResponse.builder()
                            .id(balance.getId())
                            .variantId(balance.getVariantId())
                            .variantLabel(variantLabels.get(balance.getVariantId()))
                            .locationId(balance.getLocationId())
                            .locationName(locationNames.get(balance.getLocationId()))
                            .onHand(balance.getOnHand())
                            .reserved(balance.getReserved())
                            .updatedAt(balance.getUpdatedAt())
                            .updatedBy(balance.getLastModifiedBy())
                            .build());
        }
        byItem.values().forEach(list -> list.sort(
                Comparator.comparing(StockRowResponse::getVariantLabel, Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(StockRowResponse::getLocationName, Comparator.nullsLast(Comparator.naturalOrder()))));
        narrowToSearch(items, byItem, needle);
        return byItem;
    }

    // A search that matched a variant value or a location name should leave only the rows it matched,
    // so the total describes what is on screen. An item matched by its own name keeps all of its rows,
    // and so does one the database matched on something this pass cannot see — the collation ignores
    // accents where Java does not, and an item with no rows at all would read as a bug.
    private static void narrowToSearch(List<InventoryItem> items, Map<Long, List<StockRowResponse>> byItem,
                                       String needle) {
        if (needle == null) {
            return;
        }
        String lower = needle.toLowerCase(Locale.ROOT);
        for (InventoryItem item : items) {
            List<StockRowResponse> rows = byItem.get(item.getId());
            if (rows == null || item.getName().toLowerCase(Locale.ROOT).contains(lower)) {
                continue;
            }
            List<StockRowResponse> kept = rows.stream().filter(r -> contains(r.getVariantLabel(), lower)
                    || contains(r.getLocationName(), lower)).toList();
            if (!kept.isEmpty()) {
                byItem.put(item.getId(), kept);
            }
        }
    }

    private static boolean contains(String value, String lower) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(lower);
    }

    // "S / Yellow" — the variant's attribute values in attribute order. Absent for the single
    // attribute-less variant an item without real variants carries.
    private Map<Long, String> variantLabels(List<Long> variantIds) {
        List<InventoryVariantAttributeValue> values = variantIds.isEmpty() ? List.of()
                : variantAttributeValueRepository.findByVariantIdIn(variantIds);
        if (values.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> options = optionRepository.findAllById(values.stream()
                        .map(InventoryVariantAttributeValue::getOptionId).filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(InventoryAttributeOption::getId, InventoryAttributeOption::getValue));
        return values.stream()
                .sorted(Comparator.comparing(InventoryVariantAttributeValue::getAttributeId))
                .collect(Collectors.groupingBy(InventoryVariantAttributeValue::getVariantId,
                        Collectors.mapping(v -> v.getOptionId() != null
                                        ? options.getOrDefault(v.getOptionId(), "")
                                        : Objects.toString(v.getRawValue(), ""),
                                Collectors.joining(" / "))));
    }

    private static StockItemResponse toStockItem(InventoryItem item, List<StockRowResponse> rows) {
        int onHand = rows.stream().mapToInt(StockRowResponse::getOnHand).sum();
        return StockItemResponse.builder()
                .itemId(item.getId())
                .itemName(item.getName())
                .onHand(onHand)
                .locationCount((int) rows.stream().map(StockRowResponse::getLocationId).distinct().count())
                .lowStockThreshold(item.getLowStockThreshold())
                // An item with no threshold of its own is low only once the shelf is empty.
                .low(onHand <= (item.getLowStockThreshold() == null ? 0 : item.getLowStockThreshold()))
                .updatedAt(rows.stream().map(StockRowResponse::getUpdatedAt).filter(Objects::nonNull)
                        .max(Comparator.naturalOrder()).orElse(null))
                .rows(rows)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<StockMovementResponse> listMovements(Long organizationId, Long itemId, Long variantId,
                                                     MovementType type, MovementReason reason, String performedBy,
                                                     Instant occurredFrom, Instant occurredTo,
                                                     Pageable pageable, User currentUser) {
        accessGuard.requireOrgAccess(currentUser, organizationId);
        if (occurredFrom != null && occurredTo != null && occurredFrom.isAfter(occurredTo)) {
            throw new InvalidUserDataException("The start of the date range must come before its end.");
        }
        // Lines posted by one operation share a clock reading, so occurredAt alone leaves ties the
        // database may break differently each time — a row could land on two pages or on none.
        Pageable effective = pageable.getSort().isSorted() ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                        Sort.by(Sort.Direction.DESC, "occurredAt", "id"));
        return movementRepository.search(organizationId, itemId, variantId, type, reason,
                        blankToNull(performedBy), occurredFrom, occurredTo, effective)
                .map(StockMovementResponse::fromEntity);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @Auditable(entityType = AuditEntityType.STOCK_MOVEMENT, action = AuditAction.CREATE)
    @Override
    @Transactional
    public StockBalanceResponse receiveStock(Long organizationId, ReceiveStockRequest request, User currentUser) {
        accessGuard.requireOrgAccess(currentUser, organizationId);
        InventoryVariant variant = requireVariant(request.getVariantId());
        requireItemInOrg(variant.getItemId(), organizationId);
        InventoryLocation location = requireLocationInOrg(request.getLocationId(), organizationId);

        InventoryStock stock = findOrCreateStock(variant.getId(), location.getId());
        addStock(stock.getId(), request.getQuantity(), currentUser);

        InventoryMovement movement = movementRepository.save(InventoryMovement.builder()
                .organizationId(organizationId)
                .itemId(variant.getItemId())
                .variantId(variant.getId())
                .type(MovementType.RECEIPT)
                .toLocationId(location.getId())
                .quantity(request.getQuantity())
                .reference(request.getReference())
                .occurredAt(Instant.now())
                .build());
        auditAgainst(organizationId, movement);

        log.info("Received {} of variant {} at location {} for organization {}",
                request.getQuantity(), variant.getId(), location.getId(), organizationId);
        return StockBalanceResponse.fromEntity(reload(stock.getId()));
    }

    @Auditable(entityType = AuditEntityType.STOCK_MOVEMENT, action = AuditAction.CREATE)
    @Override
    @Transactional
    public List<StockBalanceResponse> transferStock(Long organizationId, TransferStockRequest request, User currentUser) {
        accessGuard.requireOrgAccess(currentUser, organizationId);
        if (request.getFromLocationId().equals(request.getToLocationId())) {
            throw new InvalidUserDataException("The source and destination locations must be different.");
        }

        InventoryVariant variant = requireVariant(request.getVariantId());
        requireItemInOrg(variant.getItemId(), organizationId);
        InventoryLocation fromLocation = requireLocationInOrg(request.getFromLocationId(), organizationId);
        InventoryLocation toLocation = requireLocationInOrg(request.getToLocationId(), organizationId);

        InventoryStock sourceStock = requireStockForDeduction(variant.getId(), fromLocation.getId());
        deductOrThrow(sourceStock.getId(), request.getQuantity(), currentUser);

        InventoryStock destStock = findOrCreateStock(variant.getId(), toLocation.getId());
        addStock(destStock.getId(), request.getQuantity(), currentUser);

        // A transfer is two ledger lines, not one row with both locations set, so each location's
        // own history reads as a plain list of what arrived or left it.
        Instant now = Instant.now();
        InventoryMovement out = movementRepository.save(InventoryMovement.builder()
                .organizationId(organizationId)
                .itemId(variant.getItemId())
                .variantId(variant.getId())
                .type(MovementType.TRANSFER)
                .fromLocationId(fromLocation.getId())
                .quantity(request.getQuantity())
                .reference(request.getReference())
                .occurredAt(now)
                .build());
        movementRepository.save(InventoryMovement.builder()
                .organizationId(organizationId)
                .itemId(variant.getItemId())
                .variantId(variant.getId())
                .type(MovementType.TRANSFER)
                .toLocationId(toLocation.getId())
                .quantity(request.getQuantity())
                .reference(request.getReference())
                .occurredAt(now)
                .build());
        auditAgainst(organizationId, out);

        log.info("Transferred {} of variant {} from location {} to location {} for organization {}",
                request.getQuantity(), variant.getId(), fromLocation.getId(), toLocation.getId(), organizationId);
        return List.of(
                StockBalanceResponse.fromEntity(reload(sourceStock.getId())),
                StockBalanceResponse.fromEntity(reload(destStock.getId())));
    }

    @Auditable(entityType = AuditEntityType.STOCK_MOVEMENT, action = AuditAction.CREATE)
    @Override
    @Transactional
    public StockBalanceResponse adjustStock(Long organizationId, AdjustStockRequest request, User currentUser) {
        accessGuard.requireOrgAccess(currentUser, organizationId);
        int delta = request.getQuantity();
        if (delta == 0) {
            throw new InvalidUserDataException("The adjustment quantity cannot be zero.");
        }
        if (!ADJUSTMENT_REASONS.contains(request.getReason())) {
            throw new InvalidUserDataException("Choose whether the stock was damaged, lost, or simply miscounted.");
        }
        // Damage and loss can only take stock away, so their sign is not the caller's to choose.
        if (request.getReason() != MovementReason.CORRECTION) {
            delta = -Math.abs(delta);
        }

        InventoryVariant variant = requireVariant(request.getVariantId());
        requireItemInOrg(variant.getItemId(), organizationId);
        InventoryLocation location = requireLocationInOrg(request.getLocationId(), organizationId);

        InventoryMovement.InventoryMovementBuilder movement = InventoryMovement.builder()
                .organizationId(organizationId)
                .itemId(variant.getItemId())
                .variantId(variant.getId())
                .type(MovementType.ADJUSTMENT)
                .reason(request.getReason())
                .quantity(Math.abs(delta))
                .occurredAt(Instant.now());

        Long stockId;
        if (delta > 0) {
            InventoryStock stock = findOrCreateStock(variant.getId(), location.getId());
            addStock(stock.getId(), delta, currentUser);
            stockId = stock.getId();
            movement.toLocationId(location.getId());
        } else {
            InventoryStock stock = requireStockForDeduction(variant.getId(), location.getId());
            deductOrThrow(stock.getId(), Math.abs(delta), currentUser);
            stockId = stock.getId();
            movement.fromLocationId(location.getId());
        }

        auditAgainst(organizationId, movementRepository.save(movement.build()));
        log.info("Adjusted variant {} at location {} by {} for organization {}, reason {}",
                variant.getId(), location.getId(), delta, organizationId, request.getReason());
        return StockBalanceResponse.fromEntity(reload(stockId));
    }

    // ---- lookups ----------------------------------------------------------------

    private InventoryVariant requireVariant(Long variantId) {
        return variantRepository.findById(variantId)
                .orElseThrow(InventoryVariantNotFoundException::new);
    }

    private void requireItemInOrg(Long itemId, Long organizationId) {
        itemRepository.findByIdAndOrganizationId(itemId, organizationId)
                .orElseThrow(InventoryVariantNotFoundException::new);
    }

    private InventoryLocation requireLocationInOrg(Long locationId, Long organizationId) {
        return locationRepository.findByIdAndOrganizationId(locationId, organizationId)
                .orElseThrow(InventoryLocationNotFoundException::new);
    }

    // ---- balance updates ----------------------------------------------------------------

    private InventoryStock findOrCreateStock(Long variantId, Long locationId) {
        return stockRepository.findByVariantIdAndLocationId(variantId, locationId)
                .orElseGet(() -> stockRepository.save(InventoryStock.builder()
                        .variantId(variantId)
                        .locationId(locationId)
                        .onHand(0)
                        .reserved(0)
                        .build()));
    }

    private InventoryStock requireStockForDeduction(Long variantId, Long locationId) {
        return stockRepository.findByVariantIdAndLocationId(variantId, locationId)
                .orElseThrow(InsufficientStockException::new);
    }

    private void addStock(Long stockId, int qty, User currentUser) {
        stockRepository.add(stockId, qty, Instant.now(), currentUser.getUsername());
    }

    private void deductOrThrow(Long stockId, int qty, User currentUser) {
        int updated = stockRepository.deduct(stockId, qty, false, Instant.now(), currentUser.getUsername());
        if (updated == 0) {
            throw new InsufficientStockException();
        }
    }

    private InventoryStock reload(Long stockId) {
        return stockRepository.findById(stockId).orElseThrow();
    }

    // A balance response carries no organization, and the aspect would otherwise fall back to the
    // actor's own — which is none at all for a root user.
    private void auditAgainst(Long organizationId, InventoryMovement movement) {
        AuditContextHolder.setOrganizationId(organizationId);
        AuditContextHolder.setEntityId(String.valueOf(movement.getId()));
    }
}
