package com.timekeeper.bibexpo.service;

import com.timekeeper.bibexpo.model.dto.request.CreateOrganizationRequest;
import com.timekeeper.bibexpo.model.dto.request.UpdateOrganizationRequest;
import com.timekeeper.bibexpo.model.dto.response.OrganizationResponse;
import com.timekeeper.bibexpo.model.entity.User;


public interface OrganizationService {

    /**
     * Create a new organization
     * @param request The organization creation request
     * @return The created organization response
     */
    OrganizationResponse createOrganization(CreateOrganizationRequest request);

    /**
     * Update an existing organization with partial data
     * @param id The organization ID
     * @param request The update request with optional fields
     * @param currentUser The authenticated user
     * @return The updated organization response
     */
    OrganizationResponse updateOrganization(Long id, UpdateOrganizationRequest request, User currentUser);

    /**
     * Enable or disable an organization
     * When an organization is disabled, all its users will be automatically disabled.
     * When an organization is enabled, its users will NOT be automatically enabled.
     * @param id The organization ID
     * @param enabled The new enabled status (true to enable, false to disable)
     * @return The updated organization response
     */
    OrganizationResponse toggleOrganizationStatus(Long id, Boolean enabled);
}
