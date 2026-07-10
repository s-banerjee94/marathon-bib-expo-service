package com.timekeeper.bibexpo.service;

import com.timekeeper.bibexpo.model.dto.request.ChangePasswordRequest;
import com.timekeeper.bibexpo.model.dto.request.CreateUserRequest;
import com.timekeeper.bibexpo.model.dto.request.UpdateUserRequest;
import com.timekeeper.bibexpo.model.dto.response.UserResponse;
import com.timekeeper.bibexpo.model.entity.UserRole;
import com.timekeeper.bibexpo.security.CurrentActor;
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
     * @param actor the authenticated user creating this user
     * @return the created user response
     * @throws com.timekeeper.bibexpo.exception.UserAlreadyExistsException if username or email already exists
     * @throws com.timekeeper.bibexpo.exception.InvalidUserDataException if validation fails or limits exceeded
     * @throws com.timekeeper.bibexpo.exception.OrganizationNotFoundException if organization is required but not found
     * @throws com.timekeeper.bibexpo.exception.UnauthorizedAccessException if user lacks permission to create requested role
     */
    UserResponse createUser(CreateUserRequest request, CurrentActor actor);

    /**
     * Validate that {@code actor} is allowed to create a user with the given role
     * and organization, applying the same hierarchy, ROOT guard, organization-scoping, and
     * organization existence/enabled checks as {@link #createUser}. Performs no writes.
     *
     * <p>Used to authorize an invite before it is issued, so an invitee can never receive a
     * link for a role or organization the inviter could not have created directly.
     *
     * @param role the role the invited user would be created with
     * @param organizationId the target organization (required for organization-scoped roles)
     * @param eventId the target event (required for DISTRIBUTOR; must belong to the organization and not have ended)
     * @param actor the authenticated user issuing the invite
     * @throws com.timekeeper.bibexpo.exception.UnauthorizedAccessException if the role is not creatable by the caller
     * @throws com.timekeeper.bibexpo.exception.InvalidUserDataException if the organization is required but missing or disabled, or the event is required but missing or has ended
     * @throws com.timekeeper.bibexpo.exception.OrganizationNotFoundException if the organization does not exist
     * @throws com.timekeeper.bibexpo.exception.EventNotFoundException if the event does not exist or is outside the organization
     */
    void assertCanCreateUser(UserRole role, Long organizationId, Long eventId, CurrentActor actor);

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
     * Only basic profile fields can be updated: email, fullName, phoneNumber.
     * The password is changed through {@link #changeOwnPassword} or the password-reset flow;
     * administrative fields (role, organization, account status) require separate admin operations.
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
     * @param actor the authenticated user performing the update
     * @return the updated user response
     * @throws com.timekeeper.bibexpo.exception.UserNotFoundException if user not found
     * @throws com.timekeeper.bibexpo.exception.UserAlreadyExistsException if email already exists (when changing email)
     * @throws com.timekeeper.bibexpo.exception.InvalidUserDataException if validation fails
     * @throws com.timekeeper.bibexpo.exception.UnauthorizedAccessException if user lacks permission to update target user
     */
    UserResponse updateUser(Long userId, UpdateUserRequest request, CurrentActor actor);

    /**
     * Change the signed-in user's own password. The current password is verified before the new
     * one is stored, and the new password must differ from the current one. No audit event is
     * recorded for a self-service change.
     *
     * @param actor   the signed-in user changing their password
     * @param request         the current and new passwords
     * @throws com.timekeeper.bibexpo.exception.InvalidUserDataException if the current password is wrong,
     *         or the new password matches the current one
     * @throws com.timekeeper.bibexpo.exception.UserNotFoundException if the current user no longer exists
     */
    void changeOwnPassword(CurrentActor actor, ChangePasswordRequest request);

    /**
     * Assert that {@code actor} is allowed to manage (update) the target user, applying
     * the same permission hierarchy as {@link #updateUser}. Performs no writes.
     *
     * <p>Used to authorize an administrator-initiated password reset link before it is issued, so a
     * reset can only ever be issued for a user the caller could otherwise have updated.
     *
     * @param userId          the target user to be managed
     * @param actor the authenticated user performing the action
     * @throws com.timekeeper.bibexpo.exception.UserNotFoundException if the target user does not exist
     * @throws com.timekeeper.bibexpo.exception.UnauthorizedAccessException if the caller lacks permission
     */
    void assertCanUpdateUser(Long userId, CurrentActor actor);

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
     * @param actor the authenticated user performing the reassignment
     * @return the updated user response
     * @throws com.timekeeper.bibexpo.exception.UserNotFoundException if the user does not exist
     * @throws com.timekeeper.bibexpo.exception.InvalidUserDataException if the target is not a distributor, or the event is missing or has ended
     * @throws com.timekeeper.bibexpo.exception.EventNotFoundException if the event does not exist or is outside the organization
     * @throws com.timekeeper.bibexpo.exception.UnauthorizedAccessException if the caller lacks permission
     */
    UserResponse reassignDistributorEvent(Long userId, Long eventId, CurrentActor actor);

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
     * @param actor the authenticated user performing the toggle
     * @return the updated user response
     * @throws com.timekeeper.bibexpo.exception.UserNotFoundException if user not found
     * @throws com.timekeeper.bibexpo.exception.UnauthorizedAccessException if user lacks permission to toggle target user
     */
    UserResponse toggleUserEnabled(Long userId, CurrentActor actor);

    /**
     * Toggle the locked status of a user account (flips accountNonLocked).
     * A locked account cannot log in. Restricted to platform administrators.
     *
     * Permission hierarchy:
     * - ROOT can lock/unlock: any user
     * - ADMIN can lock/unlock: any user
     *
     * @param userId the ID of the user to toggle
     * @param actor the authenticated user performing the toggle
     * @return the updated user response
     * @throws com.timekeeper.bibexpo.exception.UserNotFoundException if user not found
     * @throws com.timekeeper.bibexpo.exception.UnauthorizedAccessException if user lacks permission to toggle target user
     */
    UserResponse toggleUserLocked(Long userId, CurrentActor actor);

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
     * @param actor the authenticated user making the request
     * @return the user response
     * @throws com.timekeeper.bibexpo.exception.UserNotFoundException if the user does not exist, is archived, or is not visible to the caller
     */
    UserResponse getUserById(Long userId, CurrentActor actor);

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
     * @param actor the authenticated user making the request
     * @return the user response
     * @throws com.timekeeper.bibexpo.exception.UserNotFoundException if the user does not exist, is archived, or is not visible to the caller
     */
    UserResponse getUserByUsername(String username, CurrentActor actor);

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
     * @param actor the authenticated user making the request
     * @return page of user responses matching filters
     * @throws com.timekeeper.bibexpo.exception.UnauthorizedAccessException if user lacks permission
     */
    Page<UserResponse> getUsers(UserRole role, Long organizationId, Long eventId, Boolean enabled,
                                String search, Pageable pageable,
                                CurrentActor actor);

    /**
     * Get the current user's profile.
     * All authenticated users can access their own profile.
     *
     * @param actor the authenticated user making the request
     * @return the current user's profile response
     * @throws com.timekeeper.bibexpo.exception.UserNotFoundException if current user not found
     */
    UserResponse getCurrentUser(CurrentActor actor);

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
     * @param actor the authenticated user performing the archive
     * @throws com.timekeeper.bibexpo.exception.UserNotFoundException if user not found
     * @throws com.timekeeper.bibexpo.exception.UnauthorizedAccessException if user lacks permission
     *         or target user is ROOT
     */
    void deleteUser(Long userId, CurrentActor actor);

    /**
     * Permanently delete every user of an organization, with no archival. For each user the
     * notifications and profile picture are removed and the auth cache is evicted; any archived
     * user records for the organization are dropped too. Intended for organization deletion,
     * where retaining user records has no value.
     *
     * @param organizationId the organization whose users are being purged
     * @return the number of live users deleted
     */
    int purgeUsersForOrganization(Long organizationId);
}
