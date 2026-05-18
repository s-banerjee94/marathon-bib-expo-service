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
     * Organization limits (maxOrganizerUsers, maxDistributors) are enforced.
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
     * @param userId the ID of the user to retrieve
     * @param currentUsername the username of the user making the request
     * @return the user response
     * @throws com.timekeeper.bibexpo.exception.UserNotFoundException if user not found or archived
     * @throws com.timekeeper.bibexpo.exception.UnauthorizedAccessException if user lacks permission to view target user
     */
    UserResponse getUserById(Long userId, String currentUsername);

    /**
     * Get users with role-based scoping.
     * ROOT/ADMIN: full access, organizationId honored.
     * ORGANIZER_ADMIN/ORGANIZER_USER/DISTRIBUTOR: auto-scoped to own org, organizationId ignored.
     *
     * @param role optional role filter
     * @param organizationId optional organization filter (ROOT/ADMIN only)
     * @param enabled optional enabled status filter
     * @param search optional search term (searches username, email, fullName)
     * @param pageable pagination parameters
     * @param currentUsername the username of the user making the request
     * @return page of user responses matching filters
     * @throws com.timekeeper.bibexpo.exception.UnauthorizedAccessException if user lacks permission
     */
    Page<UserResponse> getUsers(UserRole role, Long organizationId, Boolean enabled,
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
}
