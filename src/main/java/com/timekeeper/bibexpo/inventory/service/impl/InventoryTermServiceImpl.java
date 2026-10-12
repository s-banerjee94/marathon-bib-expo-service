package com.timekeeper.bibexpo.inventory.service.impl;

import com.timekeeper.bibexpo.inventory.exception.InventoryTermAlreadyExistsException;
import com.timekeeper.bibexpo.inventory.exception.InventoryTermInUseException;
import com.timekeeper.bibexpo.inventory.exception.InventoryTermLimitReachedException;
import com.timekeeper.bibexpo.inventory.exception.InventoryTermNotFoundException;
import com.timekeeper.bibexpo.inventory.model.dto.request.CreateInventoryTermRequest;
import com.timekeeper.bibexpo.inventory.model.dto.request.UpdateInventoryTermRequest;
import com.timekeeper.bibexpo.inventory.model.dto.response.InventoryTermListResponse;
import com.timekeeper.bibexpo.inventory.model.dto.response.InventoryTermResponse;
import com.timekeeper.bibexpo.inventory.model.entity.InventoryTerm;
import com.timekeeper.bibexpo.inventory.model.enums.TermKind;
import com.timekeeper.bibexpo.inventory.repository.InventoryItemRepository;
import com.timekeeper.bibexpo.inventory.repository.InventoryLocationRepository;
import com.timekeeper.bibexpo.inventory.repository.InventoryTermRepository;
import com.timekeeper.bibexpo.inventory.service.InventoryTermService;
import com.timekeeper.bibexpo.inventory.service.util.InventoryLimitLog;
import com.timekeeper.bibexpo.inventory.service.validator.InventoryAccessGuard;
import com.timekeeper.bibexpo.organization.api.InventoryQuota;
import com.timekeeper.bibexpo.user.model.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryTermServiceImpl implements InventoryTermService {

    private static final Map<TermKind, List<String>> STARTER_TERMS = new EnumMap<>(Map.of(
            TermKind.ITEM_CATEGORY, List.of("Apparel", "Medals", "Bags & Kits", "Bottles & Sippers",
                    "Nutrition", "Race Essentials", "Merchandise"),
            TermKind.LOCATION_TYPE, List.of("Warehouse", "Expo Counter", "Race-Day Zone", "In Transit"),
            TermKind.UNIT, List.of("Piece", "Pair", "Pack", "Set", "Box", "Carton")));

    private final InventoryTermRepository termRepository;
    private final InventoryItemRepository itemRepository;
    private final InventoryLocationRepository locationRepository;
    private final InventoryQuota inventoryQuota;
    private final InventoryAccessGuard accessGuard;

    @Override
    @Transactional(readOnly = true)
    public InventoryTermListResponse listVisible(Long organizationId, TermKind kind, User currentUser) {
        accessGuard.requireOrgAccess(currentUser, organizationId);
        return InventoryTermListResponse.builder()
                .organizationTerms(termRepository.findByKindAndOrganizationIdOrderByName(kind, organizationId)
                        .stream()
                        .map(InventoryTermResponse::fromEntity)
                        .toList())
                .build();
    }

    @Override
    @Transactional
    public InventoryTermResponse createTerm(Long organizationId, TermKind kind,
                                             CreateInventoryTermRequest request, User currentUser) {
        accessGuard.requireOrgAccess(currentUser, organizationId);
        reserveTermSlot(organizationId);

        String name = request.getName().trim();
        if (existsByName(kind, organizationId, name)) {
            throw new InventoryTermAlreadyExistsException();
        }

        InventoryTerm term = termRepository.save(InventoryTerm.builder()
                .kind(kind)
                .organizationId(organizationId)
                .name(name)
                .build());

        logTerm("inventory.term_created", "Inventory term created", term);
        return InventoryTermResponse.fromEntity(term);
    }

    @Override
    @Transactional
    public InventoryTermResponse updateTerm(Long organizationId, Long termId,
                                             UpdateInventoryTermRequest request, User currentUser) {
        accessGuard.requireOrgAccess(currentUser, organizationId);
        InventoryTerm term = requireTermInScope(termId, organizationId);

        String name = request.getName().trim();
        if (!name.equalsIgnoreCase(term.getName()) && existsByName(term.getKind(), organizationId, name)) {
            throw new InventoryTermAlreadyExistsException();
        }
        term.setName(name);

        InventoryTerm saved = termRepository.saveAndFlush(term);
        logTerm("inventory.term_renamed", "Inventory term renamed", saved);
        return InventoryTermResponse.fromEntity(saved);
    }

    @Override
    @Transactional
    public void deleteTerm(Long organizationId, Long termId, User currentUser) {
        accessGuard.requireOrgAccess(currentUser, organizationId);
        InventoryTerm term = requireTermInScope(termId, organizationId);

        boolean inUse = itemRepository.countByCategoryId(termId) > 0
                || itemRepository.countByUnitId(termId) > 0
                || locationRepository.countByTypeId(termId) > 0;
        if (inUse) {
            throw new InventoryTermInUseException();
        }

        termRepository.delete(term);
        inventoryQuota.releaseTerm(organizationId);
        logTerm("inventory.term_deleted", "Inventory term deleted", term);
    }

    // Called after the organization commits, so it needs a transaction of its own.
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void seedStarterTerms(Long organizationId) {
        List<InventoryTerm> terms = STARTER_TERMS.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream().map(name -> InventoryTerm.builder()
                        .kind(entry.getKey())
                        .organizationId(organizationId)
                        .name(name)
                        .build()))
                .toList();

        int slots = 0;
        while (slots < terms.size() && inventoryQuota.tryReserveTerm(organizationId)) {
            slots++;
        }
        termRepository.saveAll(terms.subList(0, slots));

        log.atInfo()
                .addKeyValue("event", "inventory.starter_terms_created")
                .addKeyValue("targetOrgId", organizationId)
                .addKeyValue("count", slots)
                .log("Starter inventory terms created");
    }

    // ---- lookups ----------------------------------------------------------------

    // Another organization's term reads as not found rather than forbidden, so a caller learns
    // nothing about vocabulary outside their own.
    private InventoryTerm requireTermInScope(Long termId, Long organizationId) {
        InventoryTerm term = termRepository.findById(termId)
                .orElseThrow(InventoryTermNotFoundException::new);
        if (!organizationId.equals(term.getOrganizationId())) {
            throw new InventoryTermNotFoundException();
        }
        return term;
    }

    private boolean existsByName(TermKind kind, Long organizationId, String name) {
        return termRepository.existsByKindAndOrganizationIdAndName(kind, organizationId, name);
    }

    // ---- access ----------------------------------------------------------------

    private void reserveTermSlot(Long organizationId) {
        if (!inventoryQuota.tryReserveTerm(organizationId)) {
            InventoryLimitLog.limitReached(organizationId, "TERMS");
            throw new InventoryTermLimitReachedException();
        }
    }

    private static void logTerm(String event, String message, InventoryTerm term) {
        log.atInfo()
                .addKeyValue("event", event)
                .addKeyValue("targetOrgId", term.getOrganizationId())
                .addKeyValue("termId", term.getId())
                .addKeyValue("kind", term.getKind())
                .log(message);
    }
}
