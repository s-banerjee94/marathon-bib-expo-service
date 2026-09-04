package com.timekeeper.bibexpo.organization.api;

import com.timekeeper.bibexpo.organization.model.entity.Organization;

/**
 * Read access to organizations for modules that hold no organization logic of their own.
 * Billing, messaging and invitations only ever ask whether an organization exists or what it is
 * called; each reached the repository directly to do it. Writes and the full organization
 * response stay behind {@code OrganizationService}.
 */
public interface OrganizationDirectory {

    /**
     * Returns the organization with the given id.
     *
     * @param organizationId the organization id
     * @return the organization, never {@code null}
     * @throws com.timekeeper.bibexpo.organization.exception.OrganizationNotFoundException
     *         if no organization has that id
     */
    Organization requireById(Long organizationId);

    /**
     * Returns the organizer name for the given id, for callers that want a label and treat a
     * missing organization as simply having none.
     *
     * @param organizationId the organization id, may be {@code null}
     * @return the organizer name, or {@code null} when the id is null or unknown
     */
    String findOrganizerName(Long organizationId);

    /**
     * Returns the inventory caps for the given organization. Platform defaults are not the
     * organization's and do not count against any of them.
     *
     * @param organizationId the organization id
     * @return the caps, all {@code 0} when the organization has no limit row, which denies creation
     */
    InventoryLimits inventoryLimits(Long organizationId);
}
