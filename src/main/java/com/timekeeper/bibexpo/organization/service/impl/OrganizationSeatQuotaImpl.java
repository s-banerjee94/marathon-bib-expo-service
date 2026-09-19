package com.timekeeper.bibexpo.organization.service.impl;

import com.timekeeper.bibexpo.organization.api.OrganizationSeatQuota;
import com.timekeeper.bibexpo.organization.repository.OrganizationLimitRepository;
import com.timekeeper.bibexpo.shared.error.InvalidUserDataException;
import com.timekeeper.bibexpo.shared.security.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Deliberately carries no transaction of its own: the reservation has to commit or roll back with
 * the user row it is reserving for, so it joins the caller's transaction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrganizationSeatQuotaImpl implements OrganizationSeatQuota {

    private final OrganizationLimitRepository organizationLimitRepository;

    @Override
    public void reserveSeat(Long organizationId, UserRole role) {
        boolean reserved;
        String limitMessage;

        switch (role) {
            case ORGANIZER_ADMIN -> {
                reserved = organizationLimitRepository.tryIncrementAdmins(organizationId) > 0;
                limitMessage = "Your organization has reached the maximum number of administrators.";
            }
            case ORGANIZER_USER -> {
                reserved = organizationLimitRepository.tryIncrementOrganizerUsers(organizationId) > 0;
                limitMessage = "Your organization has reached the maximum number of organizer users.";
            }
            case DISTRIBUTOR -> {
                reserved = organizationLimitRepository.tryIncrementDistributors(organizationId) > 0;
                limitMessage = "Your organization has reached the maximum number of distributors.";
            }
            default -> {
                return;
            }
        }

        if (!reserved) {
            log.error("Organization {} has reached its {} limit", organizationId, role);
            throw new InvalidUserDataException(limitMessage);
        }
    }

    @Override
    public void releaseSeat(Long organizationId, UserRole role) {
        switch (role) {
            case ORGANIZER_ADMIN -> organizationLimitRepository.decrementAdmins(organizationId);
            case ORGANIZER_USER -> organizationLimitRepository.decrementOrganizerUsers(organizationId);
            case DISTRIBUTOR -> organizationLimitRepository.decrementDistributors(organizationId);
            default -> { /* system roles are not organization-scoped */ }
        }
    }
}
