package com.timekeeper.bibexpo.organization.service.impl;

import com.timekeeper.bibexpo.organization.api.InventoryQuota;
import com.timekeeper.bibexpo.organization.repository.OrganizationLimitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Deliberately carries no transaction of its own: a reservation has to commit or roll back with
 * the row it is reserving for, so it joins the caller's transaction.
 */
@Service
@RequiredArgsConstructor
public class InventoryQuotaImpl implements InventoryQuota {

    private final OrganizationLimitRepository organizationLimitRepository;

    @Override
    public boolean tryReserveTerm(Long organizationId) {
        return organizationLimitRepository.tryIncrementInventoryTerms(organizationId) > 0;
    }

    @Override
    public void releaseTerm(Long organizationId) {
        organizationLimitRepository.decrementInventoryTerms(organizationId);
    }

    @Override
    public boolean tryReserveLocation(Long organizationId) {
        return organizationLimitRepository.tryIncrementInventoryLocations(organizationId) > 0;
    }

    @Override
    public void releaseLocation(Long organizationId) {
        organizationLimitRepository.decrementInventoryLocations(organizationId);
    }
}
