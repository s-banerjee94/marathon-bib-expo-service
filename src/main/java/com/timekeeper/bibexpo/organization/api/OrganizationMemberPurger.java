package com.timekeeper.bibexpo.organization.api;

/**
 * The bulk operations disabling or deleting an organization has to perform on its members.
 * Declared here and implemented by the user module: an organization decides <em>when</em> its
 * members are locked, unlocked or purged, the user module owns <em>how</em>, and the dependency still
 * runs user to organization rather than both ways.
 */
public interface OrganizationMemberPurger {

    /**
     * Locks every member not already locked, marking the lock as caused by the organization being
     * disabled, and drops them from the auth cache so no existing session keeps working. Members
     * already locked keep their own lock and reason.
     *
     * @param organizationId the organization being disabled
     */
    void lockMembers(Long organizationId);

    /**
     * Unlocks the members locked only because the organization was disabled. Members locked for any
     * other reason stay locked.
     *
     * @param organizationId the organization whose members are being unlocked
     * @return the number of members unlocked
     */
    int unlockMembersLockedByOrganization(Long organizationId);

    /**
     * Deletes every user belonging to the organization together with their notifications, profile
     * pictures and archived rows, leaving nothing that still references the organization row.
     *
     * @param organizationId the organization whose members are being purged
     * @return the number of live users deleted
     */
    int purgeMembers(Long organizationId);
}
