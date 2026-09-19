package com.timekeeper.bibexpo.organization.api;

import com.timekeeper.bibexpo.shared.security.UserRole;

/**
 * The per-organization cap on how many users of each role may exist, and the reservation that
 * enforces it. The caps and counters belong to the organization; the user module reserves and
 * releases seats as accounts are created and removed, so this port is what keeps that dependency
 * pointing one way.
 */
public interface OrganizationSeatQuota {

    /**
     * Reserves one seat for the given role. The cap check and the increment are a single atomic
     * update, so two concurrent creates cannot both take the last remaining seat. Roles that are
     * not organization-scoped are ignored.
     *
     * @param organizationId the organization to reserve against
     * @param role           the role of the user being created
     * @throws com.timekeeper.bibexpo.shared.error.InvalidUserDataException
     *         if the organization has no seat left for that role
     */
    void reserveSeat(Long organizationId, UserRole role);

    /**
     * Releases the seat held by a role when the user is removed. The decrement is floored at zero,
     * so a release with no matching reservation can never drive a counter negative.
     *
     * @param organizationId the organization to release against
     * @param role           the role of the user being removed
     */
    void releaseSeat(Long organizationId, UserRole role);
}
