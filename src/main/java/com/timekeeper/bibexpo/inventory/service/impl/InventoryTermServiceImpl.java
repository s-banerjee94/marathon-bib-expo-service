package com.timekeeper.bibexpo.inventory.service.impl;

import com.timekeeper.bibexpo.inventory.exception.InventoryTermAlreadyExistsException;
import com.timekeeper.bibexpo.inventory.exception.InventoryTermInUseException;
import com.timekeeper.bibexpo.inventory.exception.InventoryTermLimitReachedException;
import com.timekeeper.bibexpo.inventory.exception.InventoryTermNotFoundException;
import com.timekeeper.bibexpo.inventory.model.dto.request.CreateInventoryTermRequest;
import com.timekeeper.bibexpo.inventory.model.dto.request.UpdateInventoryTermRequest;
import com.timekeeper.bibexpo.inventory.model.dto.response.InventoryTermResponse;
import com.timekeeper.bibexpo.inventory.model.entity.InventoryTerm;
import com.timekeeper.bibexpo.inventory.model.enums.TermKind;
import com.timekeeper.bibexpo.inventory.repository.InventoryItemRepository;
import com.timekeeper.bibexpo.inventory.repository.InventoryLocationRepository;
import com.timekeeper.bibexpo.inventory.repository.InventoryTermRepository;
import com.timekeeper.bibexpo.inventory.service.InventoryTermService;
import com.timekeeper.bibexpo.organization.api.OrganizationDirectory;
import com.timekeeper.bibexpo.shared.error.AccessForbiddenException;
import com.timekeeper.bibexpo.shared.security.UserRole;
import com.timekeeper.bibexpo.user.model.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryTermServiceImpl implements InventoryTermService {

    private final InventoryTermRepository termRepository;
    private final InventoryItemRepository itemRepository;
    private final InventoryLocationRepository locationRepository;
    private final OrganizationDirectory organizationDirectory;

    @Override
    @Transactional(readOnly = true)
    public List<InventoryTermResponse> listVisible(Long organizationId, TermKind kind, User currentUser) {
        if (organizationId != null) {
            authorizeOrgAccess(currentUser, organizationId);
            return toResponses(termRepository.findVisible(kind, organizationId));
        }
        return toResponses(termRepository.findByKindAndOrganizationIdIsNullOrderByName(kind));
    }

    @Override
    @Transactional(readOnly = true)
    public InventoryTermResponse getTerm(Long organizationId, Long termId, User currentUser) {
        if (organizationId != null) {
            authorizeOrgAccess(currentUser, organizationId);
        }
        return InventoryTermResponse.fromEntity(requireTermInScope(termId, organizationId));
    }

    @Override
    @Transactional
    public InventoryTermResponse createTerm(Long organizationId, TermKind kind,
                                             CreateInventoryTermRequest request, User currentUser) {
        if (organizationId != null) {
            authorizeOrgAccess(currentUser, organizationId);
            enforceTermLimit(organizationId);
        }

        String name = request.getName().trim();
        if (existsByName(kind, organizationId, name)) {
            throw new InventoryTermAlreadyExistsException();
        }

        InventoryTerm term = termRepository.save(InventoryTerm.builder()
                .kind(kind)
                .organizationId(organizationId)
                .name(name)
                .build());

        log.info("Created inventory term '{}' ({}) for organization {}", name, kind, organizationId);
        return InventoryTermResponse.fromEntity(term);
    }

    @Override
    @Transactional
    public InventoryTermResponse updateTerm(Long organizationId, Long termId,
                                             UpdateInventoryTermRequest request, User currentUser) {
        if (organizationId != null) {
            authorizeOrgAccess(currentUser, organizationId);
        }
        InventoryTerm term = requireTermInScope(termId, organizationId);

        String name = request.getName().trim();
        if (!name.equalsIgnoreCase(term.getName()) && existsByName(term.getKind(), organizationId, name)) {
            throw new InventoryTermAlreadyExistsException();
        }
        term.setName(name);

        InventoryTerm saved = termRepository.save(term);
        log.info("Renamed inventory term {} to '{}' for organization {}", termId, name, organizationId);
        return InventoryTermResponse.fromEntity(saved);
    }

    @Override
    @Transactional
    public void deleteTerm(Long organizationId, Long termId, User currentUser) {
        if (organizationId != null) {
            authorizeOrgAccess(currentUser, organizationId);
        }
        InventoryTerm term = requireTermInScope(termId, organizationId);

        boolean inUse = itemRepository.countByCategoryId(termId) > 0
                || itemRepository.countByUnitId(termId) > 0
                || locationRepository.countByTypeId(termId) > 0;
        if (inUse) {
            throw new InventoryTermInUseException();
        }

        termRepository.delete(term);
        log.info("Deleted inventory term {} for organization {}", termId, organizationId);
    }

    // ---- lookups ----------------------------------------------------------------

    // A term outside the caller's scope reads as not found, so a platform default cannot be
    // renamed through the organization path, nor an organization's term through the platform one.
    private InventoryTerm requireTermInScope(Long termId, Long organizationId) {
        InventoryTerm term = termRepository.findById(termId)
                .orElseThrow(InventoryTermNotFoundException::new);
        if (!Objects.equals(term.getOrganizationId(), organizationId)) {
            throw new InventoryTermNotFoundException();
        }
        return term;
    }

    private boolean existsByName(TermKind kind, Long organizationId, String name) {
        return organizationId != null
                ? termRepository.existsByKindAndOrganizationIdAndName(kind, organizationId, name)
                : termRepository.existsByKindAndOrganizationIdIsNullAndName(kind, name);
    }

    private List<InventoryTermResponse> toResponses(List<InventoryTerm> terms) {
        return terms.stream().map(InventoryTermResponse::fromEntity).toList();
    }

    // ---- access ----------------------------------------------------------------

    // Check-then-act rather than an atomic reserve like OrganizationSeatQuota: terms are not
    // billable, so a double-click racing past the cap by one costs nothing worth a counter column.
    private void enforceTermLimit(Long organizationId) {
        if (termRepository.countByOrganizationId(organizationId)
                >= organizationDirectory.maxInventoryTerms(organizationId)) {
            log.error("Organization {} has reached its inventory term limit", organizationId);
            throw new InventoryTermLimitReachedException();
        }
    }

    private void authorizeOrgAccess(User currentUser, Long organizationId) {
        organizationDirectory.requireById(organizationId);
        UserRole role = currentUser.getRole();
        if (role == UserRole.ROOT || role == UserRole.ADMIN) {
            return;
        }
        if (currentUser.getOrganization() == null
                || !currentUser.getOrganization().getId().equals(organizationId)) {
            throw new AccessForbiddenException("You do not have access to this organization's inventory.");
        }
    }
}
