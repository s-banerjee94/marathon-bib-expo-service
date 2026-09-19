package com.timekeeper.bibexpo.organization.service.impl;

import com.timekeeper.bibexpo.organization.api.InventoryLimits;
import com.timekeeper.bibexpo.organization.api.OrganizationDirectory;
import com.timekeeper.bibexpo.organization.exception.OrganizationNotFoundException;
import com.timekeeper.bibexpo.organization.model.entity.Organization;
import com.timekeeper.bibexpo.organization.repository.OrganizationLimitRepository;
import com.timekeeper.bibexpo.organization.service.cache.OrganizationCache;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrganizationDirectoryImpl implements OrganizationDirectory {

    private final OrganizationCache organizationCache;
    private final OrganizationLimitRepository organizationLimitRepository;

    @Override
    public Organization requireById(Long organizationId) {
        Organization organization = findOrNull(organizationId);
        if (organization == null) {
            throw new OrganizationNotFoundException();
        }
        return organization;
    }

    @Override
    public String findOrganizerName(Long organizationId) {
        Organization organization = findOrNull(organizationId);
        return organization == null ? null : organization.getOrganizerName();
    }

    @Override
    public InventoryLimits inventoryLimits(Long organizationId) {
        return organizationLimitRepository.findById(organizationId)
                .map(limit -> new InventoryLimits(limit.getMaxInventoryTerms(),
                        limit.getMaxInventoryAttributeOptions(),
                        limit.getMaxVariantAttributesPerItem(),
                        limit.getMaxItemVariants(),
                        limit.getMaxInventoryLocations()))
                .orElse(new InventoryLimits(0, 0, 0, 0, 0));
    }

    private Organization findOrNull(Long organizationId) {
        return organizationId == null ? null : organizationCache.findById(organizationId);
    }
}
