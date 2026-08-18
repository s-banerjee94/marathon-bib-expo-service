package com.timekeeper.bibexpo.organization.service;

import com.timekeeper.bibexpo.organization.model.dto.request.CreateOrganizationRequest;
import com.timekeeper.bibexpo.organization.model.dto.request.UpdateOrganizationRequest;
import com.timekeeper.bibexpo.organization.model.dto.response.OrganizationResponse;
import com.timekeeper.bibexpo.shared.security.CurrentActor;
import com.timekeeper.bibexpo.storage.model.dto.response.PresignUploadResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;


public interface OrganizationService {

    /**
     * Get all organizations with optional filters and pagination
     * Only ROOT and ADMIN users can access this method
     * @param enabled Filter by enabled status (null for all)
     * @param search Search across organizer name, email, and phone number (partial match, case-insensitive, null for all)
     * @param pageable Pagination parameters
     * @param actor The authenticated user
     * @return Page of organization responses
     */
    Page<OrganizationResponse> getAllOrganizations(Boolean enabled, String search, Pageable pageable, CurrentActor actor);

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
     * @param actor The authenticated user
     * @return The updated organization response
     */
    OrganizationResponse updateOrganization(Long id, UpdateOrganizationRequest request, CurrentActor actor);

    /**
     * Enable or disable an organization
     * When an organization is disabled, all its users will be automatically disabled.
     * When an organization is enabled, its users will NOT be automatically enabled.
     * @param id The organization ID
     * @param enabled The new enabled status (true to enable, false to disable)
     * @return The updated organization response
     */
    OrganizationResponse toggleOrganizationStatus(Long id, Boolean enabled);

    /**
     * Permanently delete an organization. Allowed only when the organization has no events
     * (no events means no bills), so nothing of record-keeping value is lost. Any users of the
     * organization are permanently deleted along with it.
     * @param id The organization ID
     * @param actor The authenticated user
     * @throws com.timekeeper.bibexpo.organization.exception.OrganizationNotFoundException if not found or deleted
     * @throws com.timekeeper.bibexpo.organization.exception.OrganizationDeletionNotAllowedException if the organization still has events
     */
    void deleteOrganization(Long id, CurrentActor actor);

    /**
     * Get organization by ID with authorization validation
     * @param id Organization ID
     * @param actor The authenticated user
     * @return Organization response
     * @throws OrganizationNotFoundException if not found or deleted
     * @throws AccessForbiddenException if user lacks permission
     */
    OrganizationResponse getOrganizationById(Long id, CurrentActor actor);

    /**
     * Get current user's organization
     * @param actor The authenticated user
     * @return Organization response
     * @throws AccessForbiddenException if user has no organization
     */
    OrganizationResponse getCurrentUserOrganization(CurrentActor actor);

    /**
     * Create a presigned S3 upload URL for an organization's logo. ROOT and ADMIN may
     * upload for any organization; ORGANIZER_ADMIN only for their own.
     * @param id The organization ID
     * @param contentType MIME type of the file (validated against allowed image types)
     * @param actor The authenticated user
     * @return the presigned upload URL plus the object key to attach afterwards
     * @throws com.timekeeper.bibexpo.organization.exception.OrganizationNotFoundException if not found or deleted
     * @throws com.timekeeper.bibexpo.shared.error.AccessForbiddenException if the caller lacks permission
     * @throws com.timekeeper.bibexpo.storage.exception.InvalidFileException if the content type is not allowed
     */
    PresignUploadResponse createLogoUploadUrl(Long id, String contentType, CurrentActor actor);

    /**
     * Attach a previously uploaded object as the organization's logo. Verifies the key
     * belongs to the organization and that the object exists, then replaces any previous
     * logo (the old object is deleted).
     * @param id The organization ID
     * @param objectKey The object key returned by the presign step
     * @param actor The authenticated user
     * @return the updated organization response (with a fresh presigned logo URL)
     * @throws com.timekeeper.bibexpo.organization.exception.OrganizationNotFoundException if not found or deleted
     * @throws com.timekeeper.bibexpo.shared.error.AccessForbiddenException if the caller lacks permission
     * @throws com.timekeeper.bibexpo.storage.exception.InvalidFileException if the key is invalid or the object is missing
     */
    OrganizationResponse attachLogo(Long id, String objectKey, CurrentActor actor);

    /**
     * Remove the organization's logo, deleting the object from S3.
     * @param id The organization ID
     * @param actor The authenticated user
     * @return the updated organization response
     * @throws com.timekeeper.bibexpo.organization.exception.OrganizationNotFoundException if not found or deleted
     * @throws com.timekeeper.bibexpo.shared.error.AccessForbiddenException if the caller lacks permission
     */
    OrganizationResponse removeLogo(Long id, CurrentActor actor);
}
