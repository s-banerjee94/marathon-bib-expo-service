package com.timekeeper.bibexpo.service;

import com.timekeeper.bibexpo.model.dto.request.CreateUserRequest;
import com.timekeeper.bibexpo.model.dto.request.UpdateUserRequest;
import com.timekeeper.bibexpo.model.dto.response.UserResponse;
import com.timekeeper.bibexpo.model.entity.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service interface for user management operations
 */
public interface UserService {

    /**
     * Create a new user with the specified role.
     * Permission hierarchy:
     * - ROOT can create: ADMIN, ORG_ADMIN, ORG_USER, DISTRIBUTOR (any organization)
     * - ADMIN can create: ORG_ADMIN, ORG_USER, DISTRIBUTOR (any organization, but NOT ADMIN)
     * - ORG_ADMIN can create: ORG_USER, DISTRIBUTOR (own organization only)
     *
     * Organization limits (administrators, organizer users, distributors) are enforced.
     *
     * @param request the user creation request
     * @param currentUsername the username of the user creating this user
     * @return the created user response
     * @throws com.timekeeper.bibexpo.exception.UserAlreadyExistsException if username or email already exists
     * @throws com.timekeeper.bibexpo.exception.InvalidUserDataException if validation fails or limits exceeded
     * @throws com.timekeeper.bibexpo.exception.OrganizationNotFoundException if organization is required but not found
     * @throws com.timekeeper.bibexpo.exception.UnauthorizedAccessException if user lacks permission to create requested role
     */
    UserResponse createUser(CreateUserRequest request, String currentUsername);

    /**
     * Validate that {@code currentUsername} is allowed to create a user with the given role
     * and organization, applying the same hierarchy, ROOT guard, organization-scoping, and
     * organization existence/enabled checks as {@link #createUser}. Performs no writes.
     *
     * <p>Used to authorize an invite before it is issued, so an invitee can never receive a
     * link for a role or organization the inviter could not have created directly.
     *
     * @param role the role the invited user would be created with
     * @param organizationId the target organization (required for organization-scoped roles)
     * @param eventId the target event (required for DISTRIBUTOR; must belong to the organization and not have ended)
     * @param currentUsername the username of the user issuing the invite
     * @throws com.timekeeper.bibexpo.exception.UnauthorizedAccessException if the role is not creatable by the caller
     * @throws com.timekeeper.bibexpo.exception.InvalidUserDataException if the organization is required but missing or disabled, or the event is required but missing or has ended
     * @throws com.timekeeper.bibexpo.exception.OrganizationNotFoundException if the organization does not exist
     * @throws com.timekeeper.bibexpo.exception.EventNotFoundException if the event does not exist or is outside the organization
     */
    void assertCanCreateUser(UserRole role, Long organizationId, Long eventId, String currentUsername);

    /**
     * Create a user from an accepted invite. Authorization is intentionally skipped here
     * because it was already enforced by {@link #assertCanCreateUser} when the invite was
     * issued; the role and organization on the request are the invitation's trusted values.
     * All payload validation, uniqueness, organization, and limit checks of {@link #createUser}
     * still apply.
     *
     * @param request the user creation request carrying the invitee's details plus the invitation's role/organization
     * @return the created user response
     * @throws com.timekeeper.bibexpo.exception.UserAlreadyExistsException if username, email, or phone already exists
     * @throws com.timekeeper.bibexpo.exception.InvalidUserDataException if validation fails or limits exceeded
     * @throws com.timekeeper.bibexpo.exception.OrganizationNotFoundException if the organization no longer exists
     */
    UserResponse createInvitedUser(CreateUserRequest request);

    /**
     * Update an existing user's profile.
     * Only basic profile fields can be updated: password, email, fullName, phoneNumber.
     * Administrative fields (role, organization, account status) require separate admin operations.
     *
     * Permission hierarchy:
     * - ROOT can update: any user
     * - ADMIN can update: itself, ORG_ADMIN, ORG_USER, DISTRIBUTOR (but not ROOT or other ADMINs)
     * - ORG_ADMIN can update: itself, ORG_USER, DISTRIBUTOR (own organization only)
     * - ORG_USER can update: itself, DISTRIBUTOR (own organization only)
     * - DISTRIBUTOR can update: itself only
     *
     * @param userId the ID of the user to update
     * @param request the user update request (only password, email, fullName, phoneNumber)
     * @param currentUsername the username of the user performing the update
     * @return the updated user response
     * @throws com.timekeeper.bibexpo.exception.UserNotFoundException if user not found
     * @throws com.timekeeper.bibexpo.exception.UserAlreadyExistsException if email already exists (when changing email)
     * @throws com.timekeeper.bibexpo.exception.InvalidUserDataException if validation fails
     * @throws com.timekeeper.bibexpo.exception.UnauthorizedAccessException if user lacks permission to update target user
     */
    UserResponse updateUser(Long userId, UpdateUserRequest request, String currentUsername);

    /**
     * Reassign a distributor to a different event within its own organization.
     *
     * <p>Only ROOT, ADMIN, ORGANIZER_ADMIN, and ORGANIZER_USER may reassign; the two
     * organization-scoped roles are limited to distributors in their own organization. The
     * target must be a DISTRIBUTOR, and the new event must belong to the distributor's
     * organization and must not have ended.
     *
     * @param userId the distributor to reassign
     * @param eventId the new event to bind the distributor to
     * @param currentUsername the username of the user performing the reassignment
     * @return the updated user response
     * @throws com.timekeeper.bibexpo.exception.UserNotFoundException if the user does not exist
     * @throws com.timekeeper.bibexpo.exception.InvalidUserDataException if the target is not a distributor, or the event is missing or has ended
     * @throws com.timekeeper.bibexpo.exception.EventNotFoundException if the event does not exist or is outside the organization
     * @throws com.timekeeper.bibexpo.exception.UnauthorizedAccessException if the caller lacks permission
     */
    UserResponse reassignDistributorEvent(Long userId, Long eventId, String currentUsername);

    /**
     * Toggle the enabled status of a user.
     * This is an administrative operation to enable/disable user accounts.
     *
     * Permission hierarchy:
     * - ROOT can disable: any user
     * - ADMIN can disable: any user
     * - ORG_ADMIN can disable: ORG_USER, DISTRIBUTOR (own organization only)
     * - ORG_USER can disable: themselves, DISTRIBUTOR (own organization only)
     *
     * @param userId the ID of the user to toggle
     * @param currentUsername the username of the user performing the toggle
     * @return the updated user response
     * @throws com.timekeeper.bibexpo.exception.UserNotFoundException if user not found
     * @throws com.timekeeper.bibexpo.exception.UnauthorizedAccessException if user lacks permission to toggle target user
     */
    UserResponse toggleUserEnabled(Long userId, String currentUsername);

    /**
     * Get a single user by ID.
     * Permission hierarchy:
     * - ROOT and ADMIN: Can get any user
     * - ORG_ADMIN, ORG_USER, DISTRIBUTOR: Can get users in their organization only
     *
     * A target the caller is not permitted to view is reported as not found, so a user's
     * existence is not disclosed across organizations or privilege levels.
     *
     * @param userId the ID of the user to retrieve
     * @param currentUsername the username of the user making the request
     * @return the user response
     * @throws com.timekeeper.bibexpo.exception.UserNotFoundException if the user does not exist, is archived, or is not visible to the caller
     */
    UserResponse getUserById(Long userId, String currentUsername);

    /**
     * Get a single user by username.
     * Permission hierarchy:
     * - ROOT and ADMIN: Can get any user
     * - ORG_ADMIN, ORG_USER: Can get users in their organization only
     *
     * A target the caller is not permitted to view is reported as not found, so a username
     * belonging to a privileged or cross-organization account cannot be distinguished from
     * a username that does not exist.
     *
     * @param username the username of the user to retrieve
     * @param currentUsername the username of the user making the request
     * @return the user response
     * @throws com.timekeeper.bibexpo.exception.UserNotFoundException if the user does not exist, is archived, or is not visible to the caller
     */
    UserResponse getUserByUsername(String username, String currentUsername);

    /**
     * Get users with role-based scoping.
     * ROOT/ADMIN: full access, organizationId honored.
     * ORGANIZER_ADMIN/ORGANIZER_USER/DISTRIBUTOR: auto-scoped to own org, organizationId ignored.
     *
     * @param role optional role filter
     * @param organizationId optional organization filter (ROOT/ADMIN only)
     * @param eventId optional event filter (matches distributors assigned to that event)
     * @param enabled optional enabled status filter
     * @param search optional search term (searches username, email, fullName)
     * @param pageable pagination parameters
     * @param currentUsername the username of the user making the request
     * @return page of user responses matching filters
     * @throws com.timekeeper.bibexpo.exception.UnauthorizedAccessException if user lacks permission
     */
    Page<UserResponse> getUsers(UserRole role, Long organizationId, Long eventId, Boolean enabled,
                                String search, Pageable pageable,
                                String currentUsername);

    /**
     * Get the current user's profile.
     * All authenticated users can access their own profile.
     *
     * @param currentUsername the username of the user making the request
     * @return the current user's profile response
     * @throws com.timekeeper.bibexpo.exception.UserNotFoundException if current user not found
     */
    UserResponse getCurrentUser(String currentUsername);

    /**
     * Archive a user. Moves the user row into the users_archive table, deletes their
     * notifications, and hard-deletes the row from the users table. This frees up
     * username/email/phoneNumber for reuse.
     *
     * ROOT users cannot be archived.
     *
     * Permission hierarchy mirrors {@link #updateUser}:
     * - ROOT can archive any non-ROOT user
     * - ADMIN can archive ORG_ADMIN, ORG_USER, DISTRIBUTOR (not itself, not other ADMINs)
     * - ORG_ADMIN can archive ORG_USER, DISTRIBUTOR in its own organization
     * - ORG_USER can archive DISTRIBUTOR in its own organization
     * - DISTRIBUTOR cannot archive users
     *
     * @param userId the id of the user to archive
     * @param currentUsername the username of the user performing the archive
     * @throws com.timekeeper.bibexpo.exception.UserNotFoundException if user not found
     * @throws com.timekeeper.bibexpo.exception.UnauthorizedAccessException if user lacks permission
     *         or target user is ROOT
     */
    void deleteUser(Long userId, String currentUsername);

    /**
     * Create a presigned S3 upload URL for a user's profile picture. The caller must
     * have permission to update the target user (same rules as {@link #updateUser}).
     *
     * @param userId          the user whose picture is being uploaded
     * @param contentType     MIME type of the file (validated against allowed image types)
     * @param currentUsername the username of the user making the request
     * @return the presigned upload URL plus the object key to attach afterwards
     * @throws com.timekeeper.bibexpo.exception.UserNotFoundException        if the user is not found
     * @throws com.timekeeper.bibexpo.exception.UnauthorizedAccessException  if the caller lacks permission
     * @throws com.timekeeper.bibexpo.exception.InvalidFileException         if the content type is not allowed
     */
    com.timekeeper.bibexpo.model.dto.response.PresignUploadResponse createProfilePictureUploadUrl(
            Long userId, String contentType, String currentUsername);

    /**
     * Attach a previously uploaded object as the user's profile picture. Verifies the
     * key belongs to the user and that the object exists in S3, then replaces any
     * previous picture (the old object is deleted).
     *
     * @param userId          the user whose picture is being set
     * @param objectKey       the object key returned by the presign step
     * @param currentUsername the username of the user making the request
     * @return the updated user response (with a fresh presigned picture URL)
     * @throws com.timekeeper.bibexpo.exception.UserNotFoundException        if the user is not found
     * @throws com.timekeeper.bibexpo.exception.UnauthorizedAccessException  if the caller lacks permission
     * @throws com.timekeeper.bibexpo.exception.InvalidFileException         if the key is invalid or the object is missing
     */
    UserResponse attachProfilePicture(Long userId, String objectKey, String currentUsername);

    /**
     * Remove the user's profile picture, deleting the object from S3.
     *
     * @param userId          the user whose picture is being removed
     * @param currentUsername the username of the user making the request
     * @return the updated user response
     * @throws com.timekeeper.bibexpo.exception.UserNotFoundException        if the user is not found
     * @throws com.timekeeper.bibexpo.exception.UnauthorizedAccessException  if the caller lacks permission
     */
    UserResponse removeProfilePicture(Long userId, String currentUsername);
}
