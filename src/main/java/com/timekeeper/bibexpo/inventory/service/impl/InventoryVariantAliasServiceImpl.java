package com.timekeeper.bibexpo.inventory.service.impl;

import com.timekeeper.bibexpo.inventory.exception.InventoryItemNotFoundException;
import com.timekeeper.bibexpo.inventory.exception.InventoryVariantAliasAlreadyExistsException;
import com.timekeeper.bibexpo.inventory.exception.InventoryVariantAliasNotFoundException;
import com.timekeeper.bibexpo.inventory.exception.InventoryVariantNotFoundException;
import com.timekeeper.bibexpo.inventory.model.dto.request.CreateInventoryVariantAliasRequest;
import com.timekeeper.bibexpo.inventory.model.dto.request.UpdateInventoryVariantAliasRequest;
import com.timekeeper.bibexpo.inventory.model.dto.response.InventoryVariantAliasResponse;
import com.timekeeper.bibexpo.inventory.model.entity.InventoryVariantAlias;
import com.timekeeper.bibexpo.inventory.repository.InventoryItemRepository;
import com.timekeeper.bibexpo.inventory.repository.InventoryVariantAliasRepository;
import com.timekeeper.bibexpo.inventory.service.InventoryVariantAliasService;
import com.timekeeper.bibexpo.inventory.service.util.VariantLabeller;
import com.timekeeper.bibexpo.inventory.service.validator.InventoryAccessGuard;
import com.timekeeper.bibexpo.user.model.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryVariantAliasServiceImpl implements InventoryVariantAliasService {

    private final InventoryVariantAliasRepository aliasRepository;
    private final InventoryItemRepository itemRepository;
    private final InventoryAccessGuard accessGuard;
    private final VariantLabeller variantLabeller;

    @Override
    @Transactional(readOnly = true)
    public List<InventoryVariantAliasResponse> listAliases(Long organizationId, Long itemId, User currentUser) {
        accessGuard.requireOrgAccess(currentUser, organizationId);
        requireItemInOrg(itemId, organizationId);

        Map<Long, String> labels = variantLabeller.labelsForItem(itemId);
        return aliasRepository.findByItemIdOrderBySourceValueAsc(itemId).stream()
                .map(alias -> InventoryVariantAliasResponse.of(alias, labels.get(alias.getVariantId())))
                .toList();
    }

    @Override
    @Transactional
    public InventoryVariantAliasResponse createAlias(Long organizationId, Long itemId,
                                                     CreateInventoryVariantAliasRequest request,
                                                     User currentUser) {
        accessGuard.requireOrgAccess(currentUser, organizationId);
        requireItemInOrg(itemId, organizationId);

        Map<Long, String> labels = variantLabeller.labelsForItem(itemId);
        requireVariantOfItem(request.getVariantId(), labels);

        String sourceValue = request.getSourceValue().trim();
        if (aliasRepository.existsByItemIdAndSourceValue(itemId, sourceValue)) {
            throw new InventoryVariantAliasAlreadyExistsException();
        }

        InventoryVariantAlias alias = aliasRepository.save(InventoryVariantAlias.builder()
                .organizationId(organizationId)
                .itemId(itemId)
                .sourceValue(sourceValue)
                .variantId(request.getVariantId())
                .build());

        log.info("Item {} now reads '{}' as variant {}", itemId, sourceValue, request.getVariantId());
        return InventoryVariantAliasResponse.of(alias, labels.get(alias.getVariantId()));
    }

    @Override
    @Transactional
    public InventoryVariantAliasResponse updateAlias(Long organizationId, Long itemId, Long aliasId,
                                                     UpdateInventoryVariantAliasRequest request,
                                                     User currentUser) {
        accessGuard.requireOrgAccess(currentUser, organizationId);
        requireItemInOrg(itemId, organizationId);

        InventoryVariantAlias alias = requireAlias(aliasId, itemId, organizationId);
        Map<Long, String> labels = variantLabeller.labelsForItem(itemId);
        requireVariantOfItem(request.getVariantId(), labels);

        alias.setVariantId(request.getVariantId());
        InventoryVariantAlias saved = aliasRepository.saveAndFlush(alias);

        log.info("Item {} now reads '{}' as variant {}", itemId, saved.getSourceValue(), request.getVariantId());
        return InventoryVariantAliasResponse.of(saved, labels.get(saved.getVariantId()));
    }

    @Override
    @Transactional
    public void deleteAlias(Long organizationId, Long itemId, Long aliasId, User currentUser) {
        accessGuard.requireOrgAccess(currentUser, organizationId);
        requireItemInOrg(itemId, organizationId);

        InventoryVariantAlias alias = requireAlias(aliasId, itemId, organizationId);
        aliasRepository.delete(alias);
        log.info("Item {} no longer reads '{}'", itemId, alias.getSourceValue());
    }

    // ---- lookups ----------------------------------------------------------------

    private void requireItemInOrg(Long itemId, Long organizationId) {
        if (itemRepository.findByIdAndOrganizationId(itemId, organizationId).isEmpty()) {
            throw new InventoryItemNotFoundException();
        }
    }

    private InventoryVariantAlias requireAlias(Long aliasId, Long itemId, Long organizationId) {
        return aliasRepository.findByIdAndItemIdAndOrganizationId(aliasId, itemId, organizationId)
                .orElseThrow(InventoryVariantAliasNotFoundException::new);
    }

    /**
     * A null variant is the deliberate "owed nothing" answer; anything else has to be one of this
     * item's own, or a spelling could point at another product's size. Membership is read from the
     * label map the caller already holds, since an item's variants are capped by plan and were
     * fetched to spell the response anyway.
     */
    private static void requireVariantOfItem(Long variantId, Map<Long, String> labelsOfItem) {
        if (variantId != null && !labelsOfItem.containsKey(variantId)) {
            throw new InventoryVariantNotFoundException();
        }
    }
}
