package com.timekeeper.bibexpo.service;

import com.timekeeper.bibexpo.model.dto.request.CreateOrganizationRequest;
import com.timekeeper.bibexpo.model.dto.request.UpdateOrganizationRequest;
import com.timekeeper.bibexpo.model.dto.response.OrganizationResponse;
import com.timekeeper.bibexpo.model.dto.response.PresignUploadResponse;
import com.timekeeper.bibexpo.model.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;


public interface OrganizationService {

    /**
     * Get all organizations with optional filters and pagination
     * Only ROOT and ADMIN users can access this method
     * @param enabled Filter by enabled status (null for all)
     * @param deleted Filter by deleted status (null for all)
     * @param search Search across organizer name, email, and phone number (partial match, case-insensitive, null for all)
     * @param pageable Pagination parameters
     * @param currentUser The authenticated user
     * @return Page of organization responses
     */
    Page<OrganizationResponse> getAllOrganizations(Boolean enabled, Boolean deleted, String search, Pageable pageable, User currentUser);

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

    /**
     * Get organization by ID with authorization validation
     * @param id Organization ID
     * @param currentUser The authenticated user
     * @return Organization response
     * @throws OrganizationNotFoundException if not found or deleted
     * @throws UnauthorizedAccessException if user lacks permission
     */
    OrganizationResponse getOrganizationById(Long id, User currentUser);

    /**
     * Get current user's organization
     * @param currentUser The authenticated user
     * @return Organization response
     * @throws UnauthorizedAccessException if user has no organization
     */
    OrganizationResponse getCurrentUserOrganization(User currentUser);

    /**
     * Create a presigned S3 upload URL for an organization's logo. ROOT and ADMIN may
     * upload for any organization; ORGANIZER_ADMIN only for their own.
     * @param id The organization ID
     * @param contentType MIME type of the file (validated against allowed image types)
     * @param currentUser The authenticated user
     * @return the presigned upload URL plus the object key to attach afterwards
     * @throws com.timekeeper.bibexpo.exception.OrganizationNotFoundException if not found or deleted
     * @throws com.timekeeper.bibexpo.exception.UnauthorizedAccessException if the caller lacks permission
     * @throws com.timekeeper.bibexpo.exception.InvalidFileException if the content type is not allowed
     */
    PresignUploadResponse createLogoUploadUrl(Long id, String contentType, User currentUser);

    /**
     * Attach a previously uploaded object as the organization's logo. Verifies the key
     * belongs to the organization and that the object exists, then replaces any previous
     * logo (the old object is deleted).
     * @param id The organization ID
     * @param objectKey The object key returned by the presign step
     * @param currentUser The authenticated user
     * @return the updated organization response (with a fresh presigned logo URL)
     * @throws com.timekeeper.bibexpo.exception.OrganizationNotFoundException if not found or deleted
     * @throws com.timekeeper.bibexpo.exception.UnauthorizedAccessException if the caller lacks permission
     * @throws com.timekeeper.bibexpo.exception.InvalidFileException if the key is invalid or the object is missing
     */
    OrganizationResponse attachLogo(Long id, String objectKey, User currentUser);

    /**
     * Remove the organization's logo, deleting the object from S3.
     * @param id The organization ID
     * @param currentUser The authenticated user
     * @return the updated organization response
     * @throws com.timekeeper.bibexpo.exception.OrganizationNotFoundException if not found or deleted
     * @throws com.timekeeper.bibexpo.exception.UnauthorizedAccessException if the caller lacks permission
     */
    OrganizationResponse removeLogo(Long id, User currentUser);
}
