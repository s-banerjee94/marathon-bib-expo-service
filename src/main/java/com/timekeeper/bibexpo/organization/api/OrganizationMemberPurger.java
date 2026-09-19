package com.timekeeper.bibexpo.organization.api;

/**
 * The bulk operations disabling or deleting an organization has to perform on its members.
 * Declared here and implemented by the user module: an organization decides <em>when</em> its
 * members are disabled or purged, the user module owns <em>how</em>, and the dependency still
 * runs user to organization rather than both ways.
 */
public interface OrganizationMemberPurger {

    /**
     * Disables every user belonging to the organization and drops them from the auth cache, so
     * members of a disabled organization cannot keep authenticating on an existing session.
     *
     * @param organizationId the organization whose members are being disabled
     */
    void disableMembers(Long organizationId);

    /**
     * Deletes every user belonging to the organization together with their notifications, profile
     * pictures and archived rows, leaving nothing that still references the organization row.
     *
     * @param organizationId the organization whose members are being purged
     * @return the number of live users deleted
     */
    int purgeMembers(Long organizationId);
}
