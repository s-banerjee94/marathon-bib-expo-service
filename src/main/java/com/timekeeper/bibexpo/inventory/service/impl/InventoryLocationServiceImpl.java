package com.timekeeper.bibexpo.inventory.service.impl;

import com.timekeeper.bibexpo.inventory.exception.InventoryLocationAlreadyExistsException;
import com.timekeeper.bibexpo.inventory.exception.InventoryLocationNotFoundException;
import com.timekeeper.bibexpo.inventory.exception.InventoryTermNotFoundException;
import com.timekeeper.bibexpo.inventory.model.dto.request.CreateInventoryLocationRequest;
import com.timekeeper.bibexpo.inventory.model.dto.request.UpdateInventoryLocationRequest;
import com.timekeeper.bibexpo.inventory.model.dto.response.InventoryLocationResponse;
import com.timekeeper.bibexpo.inventory.model.entity.InventoryLocation;
import com.timekeeper.bibexpo.inventory.model.entity.InventoryTerm;
import com.timekeeper.bibexpo.inventory.model.enums.TermKind;
import com.timekeeper.bibexpo.inventory.repository.InventoryLocationRepository;
import com.timekeeper.bibexpo.inventory.repository.InventoryTermRepository;
import com.timekeeper.bibexpo.inventory.service.InventoryLocationService;
import com.timekeeper.bibexpo.inventory.service.validator.InventoryAccessGuard;
import com.timekeeper.bibexpo.user.model.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryLocationServiceImpl implements InventoryLocationService {

    private final InventoryLocationRepository locationRepository;
    private final InventoryTermRepository termRepository;
    private final InventoryAccessGuard accessGuard;

    @Override
    @Transactional(readOnly = true)
    public List<InventoryLocationResponse> listLocations(Long organizationId, User currentUser) {
        accessGuard.requireOrgAccess(currentUser, organizationId);
        return locationRepository.findByOrganizationId(organizationId).stream()
                .map(InventoryLocationResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional
    public InventoryLocationResponse createLocation(Long organizationId, CreateInventoryLocationRequest request,
                                                      User currentUser) {
        accessGuard.requireOrgAccess(currentUser, organizationId);

        requireVisibleTerm(request.getTypeId(), organizationId);

        String name = request.getName().trim();
        if (locationRepository.existsByOrganizationIdAndName(organizationId, name)) {
            throw new InventoryLocationAlreadyExistsException();
        }

        InventoryLocation location = locationRepository.save(InventoryLocation.builder()
                .organizationId(organizationId)
                .name(name)
                .typeId(request.getTypeId())
                .build());

        log.info("Created inventory location '{}' for organization {}", name, organizationId);
        return InventoryLocationResponse.fromEntity(location);
    }

    @Override
    @Transactional
    public InventoryLocationResponse updateLocation(Long organizationId, Long locationId,
                                                      UpdateInventoryLocationRequest request, User currentUser) {
        accessGuard.requireOrgAccess(currentUser, organizationId);
        InventoryLocation location = locationRepository.findByIdAndOrganizationId(locationId, organizationId)
                .orElseThrow(InventoryLocationNotFoundException::new);

        if (request.getName() != null) {
            String name = request.getName().trim();
            if (!name.equalsIgnoreCase(location.getName())
                    && locationRepository.existsByOrganizationIdAndName(organizationId, name)) {
                throw new InventoryLocationAlreadyExistsException();
            }
            location.setName(name);
        }
        if (request.getTypeId() != null) {
            requireVisibleTerm(request.getTypeId(), organizationId);
            location.setTypeId(request.getTypeId());
        }

        InventoryLocation saved = locationRepository.saveAndFlush(location);
        log.info("Updated inventory location {} for organization {}", locationId, organizationId);
        return InventoryLocationResponse.fromEntity(saved);
    }

    // ---- lookups ----------------------------------------------------------------

    private void requireVisibleTerm(Long termId, Long organizationId) {
        InventoryTerm term = termRepository.findById(termId)
                .orElseThrow(InventoryTermNotFoundException::new);
        boolean visible = term.getKind() == TermKind.LOCATION_TYPE
                && (term.getOrganizationId() == null || term.getOrganizationId().equals(organizationId));
        if (!visible) {
            throw new InventoryTermNotFoundException();
        }
    }
}
