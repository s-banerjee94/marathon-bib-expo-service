package com.timekeeper.bibexpo.service.impl;

import com.timekeeper.bibexpo.exception.InvalidUserDataException;
import com.timekeeper.bibexpo.exception.OrganizationNotFoundException;
import com.timekeeper.bibexpo.exception.UnauthorizedAccessException;
import com.timekeeper.bibexpo.exception.UserAlreadyExistsException;
import com.timekeeper.bibexpo.exception.UserNotFoundException;
import com.timekeeper.bibexpo.model.dto.request.CreateUserRequest;
import com.timekeeper.bibexpo.model.dto.request.UpdateUserRequest;
import com.timekeeper.bibexpo.model.dto.response.UserResponse;
import com.timekeeper.bibexpo.model.entity.Organization;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.entity.UserRole;
import com.timekeeper.bibexpo.repository.OrganizationRepository;
import com.timekeeper.bibexpo.repository.UserRepository;
import com.timekeeper.bibexpo.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * Implementation of UserService for user management operations
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public UserResponse createUser(CreateUserRequest request, String currentUsername) {
        log.info("Creating user with username: {} by: {}", request.getUsername(), currentUsername);

        User currentUser = fetchCurrentUser(currentUsername);
        validateRootCreationAttempt(request.getRole(), currentUsername);
        validateCreateUserAuthorization(currentUser, request.getRole(), request.getOrganizationId());
        validateEmailAndPhoneRequirements(request);
        validateUniqueness(request);

        Organization organization = fetchAndValidateOrganization(request);
        User user = buildUserEntity(request, organization);
        User savedUser = userRepository.save(user);

        log.info("Successfully created user with ID: {} and role: {}", savedUser.getId(), savedUser.getRole());
        return UserResponse.fromEntity(savedUser);
    }

    /**
     * Fetch the current user who is creating a new user
     */
    private User fetchCurrentUser(String currentUsername) {
        return userRepository.findByUsernameAndDeletedFalse(currentUsername)
                .orElseThrow(() -> {
                    log.error("Current user not found: {}", currentUsername);
                    return new InvalidUserDataException("Current user not found: " + currentUsername);
                });
    }

    /**
     * Validate that ROOT cannot be created via API
     */
    private void validateRootCreationAttempt(UserRole requestedRole, String currentUsername) {
        if (requestedRole == UserRole.ROOT) {
            log.error("Attempt to create ROOT user by: {}", currentUsername);
            throw new UnauthorizedAccessException("Cannot create ROOT user. Root user is system-initialized only.");
        }
    }

    /**
     * Validate email and phone requirements based on role
     */
    private void validateEmailAndPhoneRequirements(CreateUserRequest request) {
        if (request.getRole() == UserRole.DISTRIBUTOR) {
            return; // Email and phone are optional for distributors
        }

        if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
            log.error("Email is required for role: {}", request.getRole());
            throw new InvalidUserDataException("Email is required for role: " + request.getRole());
        }

        if (request.getPhoneNumber() == null || request.getPhoneNumber().trim().isEmpty()) {
            log.error("Phone number is required for role: {}", request.getRole());
            throw new InvalidUserDataException("Phone number is required for role: " + request.getRole());
        }
    }

    /**
     * Validate username, email, and phone number uniqueness
     */
    private void validateUniqueness(CreateUserRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            log.error("Username already exists: {}", request.getUsername());
            throw new UserAlreadyExistsException("Username '" + request.getUsername() + "' already exists");
        }

        if (request.getEmail() != null && !request.getEmail().trim().isEmpty()
                && userRepository.existsByEmail(request.getEmail())) {
            log.error("Email already exists: {}", request.getEmail());
            throw new UserAlreadyExistsException("Email '" + request.getEmail() + "' already exists");
        }

        if (request.getPhoneNumber() != null && !request.getPhoneNumber().trim().isEmpty()
                && userRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            log.error("Phone number already exists: {}", request.getPhoneNumber());
            throw new UserAlreadyExistsException("Phone number '" + request.getPhoneNumber() + "' already exists");
        }
    }

    /**
     * Fetch and validate organization for organization-scoped roles
     */
    private Organization fetchAndValidateOrganization(CreateUserRequest request) {
        if (!isOrganizationRole(request.getRole())) {
            if (isSystemRole(request.getRole()) && request.getOrganizationId() != null) {
                log.warn("Organization ID provided for system role {}, will be ignored", request.getRole());
            }
            return null;
        }

        if (request.getOrganizationId() == null) {
            log.error("Organization ID is required for role: {}", request.getRole());
            throw new InvalidUserDataException("Organization ID is required for role: " + request.getRole());
        }

        Organization organization = organizationRepository.findById(request.getOrganizationId())
                .filter(org -> !org.getDeleted())
                .orElseThrow(() -> {
                    log.error("Organization not found or deleted with ID: {}", request.getOrganizationId());
                    return new OrganizationNotFoundException(
                            "Organization not found with ID: " + request.getOrganizationId());
                });

        if (Boolean.FALSE.equals(organization.getEnabled()) || Boolean.TRUE.equals(organization.getDeleted())) {
            log.error("Cannot create user for disabled organization ID: {}", request.getOrganizationId());
            throw new InvalidUserDataException("Cannot create user for disabled organization");
        }

        validateOrganizationLimits(organization, request.getRole());
        return organization;
    }

    /**
     * Build user entity from request
     */
    private User buildUserEntity(CreateUserRequest request, Organization organization) {
        return User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .fullName(request.getFullName())
                .phoneNumber(request.getPhoneNumber())
                .role(request.getRole())
                .organization(organization)
                .enabled(request.getEnabled() != null ? request.getEnabled() : true)
                .accountNonExpired(request.getAccountNonExpired() != null ? request.getAccountNonExpired() : true)
                .accountNonLocked(request.getAccountNonLocked() != null ? request.getAccountNonLocked() : true)
                .credentialsNonExpired(request.getCredentialsNonExpired() != null ? request.getCredentialsNonExpired() : true)
                .deleted(false)
                .build();
    }

    /**
     * Check if the role requires an organization
     */
    private boolean isOrganizationRole(UserRole role) {
        return role == UserRole.ORGANIZER_ADMIN ||
               role == UserRole.ORGANIZER_USER ||
               role == UserRole.DISTRIBUTOR;
    }

    /**
     * Check if the role is a system-level role
     */
    private boolean isSystemRole(UserRole role) {
        return role == UserRole.ROOT ||
               role == UserRole.ADMIN;
    }

    /**
     * Validates that the current user has permission to create a user with the requested role
     * and within the specified organization.
     *
     * @param currentUser The user attempting to create a new user
     * @param requestedRole The role being assigned to the new user
     * @param targetOrganizationId The organization ID for the new user (can be null for system roles)
     */
    private void validateCreateUserAuthorization(User currentUser, UserRole requestedRole, Long targetOrganizationId) {
        UserRole currentRole = currentUser.getRole();

        // Rule 1: Only ROOT can create ADMIN users
        if (requestedRole == UserRole.ADMIN && currentRole != UserRole.ROOT) {
            log.error("User {} with role {} attempted to create ADMIN user",
                    currentUser.getUsername(), currentRole);
            throw new UnauthorizedAccessException(
                    "Only ROOT users can create ADMIN users");
        }

        // Rule 2: ORG_ADMIN has restricted permissions
        if (currentRole == UserRole.ORGANIZER_ADMIN) {
            // ORG_ADMIN cannot create another ORG_ADMIN (already blocked: ROOT, ADMIN)
            if (requestedRole == UserRole.ORGANIZER_ADMIN) {
                log.error("ORG_ADMIN {} attempted to create another ORG_ADMIN user",
                        currentUser.getUsername());
                throw new UnauthorizedAccessException(
                        "Organization administrators cannot create other ORG_ADMIN users");
            }

            // Rule 3: ORG_ADMIN can only create users within their own organization
            if (currentUser.getOrganization() == null) {
                log.error("ORG_ADMIN {} has no organization assigned", currentUser.getUsername());
                throw new UnauthorizedAccessException(
                        "Organization administrator must be assigned to an organization");
            }

            if (targetOrganizationId == null ||
                    !currentUser.getOrganization().getId().equals(targetOrganizationId)) {
                log.error("ORG_ADMIN {} attempted to create user for different organization. " +
                                "Own org: {}, Target org: {}",
                        currentUser.getUsername(),
                        currentUser.getOrganization().getId(),
                        targetOrganizationId);
                throw new UnauthorizedAccessException(
                        "You can only create users within your own organization");
            }
        }

        // ADMIN and ROOT can create any allowed role (already validated above)
        log.debug("Authorization validated for {} to create role {}",
                currentUser.getUsername(), requestedRole);
    }

    /**
     * Validates that creating a new user won't exceed the organization's user limits.
     *
     * @param organization The organization to check limits for
     * @param requestedRole The role being assigned to the new user
     */
    private void validateOrganizationLimits(Organization organization, UserRole requestedRole) {
        if (organization == null) {
            // System-level roles don't have organization limits
            return;
        }

        if (requestedRole == UserRole.DISTRIBUTOR) {
            // Check distributor limit
            int maxDistributors = organization.getMaxDistributors();
            if (maxDistributors > 0) { // 0 means unlimited
                long currentDistributorCount = userRepository.countByOrganizationIdAndRoleAndDeletedFalse(
                        organization.getId(), UserRole.DISTRIBUTOR);

                if (currentDistributorCount >= maxDistributors) {
                    log.error("Organization {} has reached maximum distributor limit: {} (current: {})",
                            organization.getId(), maxDistributors, currentDistributorCount);
                    throw new InvalidUserDataException(
                            String.format("Organization has reached maximum distributor limit of %d. " +
                                    "Current count: %d", maxDistributors, currentDistributorCount));
                }
                log.debug("Distributor limit check passed for organization {}: {}/{}",
                        organization.getId(), currentDistributorCount + 1, maxDistributors);
            }
        } else if (requestedRole == UserRole.ORGANIZER_ADMIN ||
                requestedRole == UserRole.ORGANIZER_USER) {
            // Check organizer user limit (combined ORG_ADMIN + ORG_USER)
            int maxOrganizerUsers = organization.getMaxOrganizerUsers();
            if (maxOrganizerUsers > 0) { // 0 means unlimited
                long currentOrgAdminCount = userRepository.countByOrganizationIdAndRoleAndDeletedFalse(
                        organization.getId(), UserRole.ORGANIZER_ADMIN);
                long currentOrgUserCount = userRepository.countByOrganizationIdAndRoleAndDeletedFalse(
                        organization.getId(), UserRole.ORGANIZER_USER);
                long totalOrganizerUsers = currentOrgAdminCount + currentOrgUserCount;

                if (totalOrganizerUsers >= maxOrganizerUsers) {
                    log.error("Organization {} has reached maximum organizer user limit: {} " +
                                    "(current ORG_ADMIN: {}, ORG_USER: {}, total: {})",
                            organization.getId(), maxOrganizerUsers,
                            currentOrgAdminCount, currentOrgUserCount, totalOrganizerUsers);
                    throw new InvalidUserDataException(
                            String.format("Organization has reached maximum organizer user limit of %d. " +
                                            "Current count: %d (ORG_ADMIN: %d, ORG_USER: %d)",
                                    maxOrganizerUsers, totalOrganizerUsers,
                                    currentOrgAdminCount, currentOrgUserCount));
                }
                log.debug("Organizer user limit check passed for organization {}: {}/{}",
                        organization.getId(), totalOrganizerUsers + 1, maxOrganizerUsers);
            }
        }
    }

    /**
     * Validates that the current user and target user belong to the same organization.
     * Used to enforce organization-scoped permissions.
     *
     * @param currentUser The user performing the action
     * @param targetUser The user being acted upon
     * @param action Description of the action being performed (for error messages)
     * @throws UnauthorizedAccessException if users are not in the same organization or organization is null
     */
    private void validateSameOrganization(User currentUser, User targetUser, String action) {
        if (currentUser.getOrganization() == null) {
            log.error("{} {} has no organization assigned", currentUser.getRole(), currentUser.getUsername());
            throw new UnauthorizedAccessException(
                    currentUser.getRole() == UserRole.ORGANIZER_ADMIN
                            ? "Organization administrator must be assigned to an organization"
                            : "Organization user must be assigned to an organization");
        }

        if (targetUser.getOrganization() == null ||
            !currentUser.getOrganization().getId().equals(targetUser.getOrganization().getId())) {
            log.error("{} {} attempted to {} user from different organization",
                    currentUser.getRole(), currentUser.getUsername(), action);
            throw new UnauthorizedAccessException(
                    "You can only " + action + " users within your own organization");
        }
    }

    /**
     * Checks if the target role is in the restricted roles list.
     *
     * @param targetRole The role to check
     * @param restrictedRoles The roles that are restricted
     * @return true if target role is in restricted roles, false otherwise
     */
    private boolean isRestrictedRole(UserRole targetRole, UserRole... restrictedRoles) {
        for (UserRole restrictedRole : restrictedRoles) {
            if (targetRole == restrictedRole) {
                return true;
            }
        }
        return false;
    }

    @Override
    @Transactional
    public UserResponse updateUser(Long userId, UpdateUserRequest request, String currentUsername) {
        log.info("Updating user with ID: {} by: {}", userId, currentUsername);

        User currentUser = fetchCurrentUser(currentUsername);
        User targetUser = fetchTargetUser(userId);

        validateUpdateUserAuthorization(currentUser, targetUser);

        // Update fields if provided
        if (request.getPassword() != null) {
            targetUser.setPassword(passwordEncoder.encode(request.getPassword()));
            log.debug("Password updated for user ID: {}", userId);
        }

        if (request.getEmail() != null) {
            validateEmailUniqueness(request.getEmail(), targetUser.getId());
            targetUser.setEmail(request.getEmail());
            log.debug("Email updated for user ID: {}", userId);
        }

        if (request.getFullName() != null) {
            targetUser.setFullName(request.getFullName());
            log.debug("Full name updated for user ID: {}", userId);
        }

        if (request.getPhoneNumber() != null) {
            validatePhoneNumberUniqueness(request.getPhoneNumber(), targetUser.getId());
            targetUser.setPhoneNumber(request.getPhoneNumber());
            log.debug("Phone number updated for user ID: {}", userId);
        }

        User updatedUser = userRepository.save(targetUser);
        log.info("Successfully updated user with ID: {}", updatedUser.getId());

        return UserResponse.fromEntity(updatedUser);
    }

    /**
     * Fetch the target user to be updated
     */
    private User fetchTargetUser(Long userId) {
        return userRepository.findById(userId)
                .filter(user -> !user.getDeleted())
                .orElseThrow(() -> {
                    log.error("User not found with ID: {}", userId);
                    return new UserNotFoundException("User not found with ID: " + userId);
                });
    }

    /**
     * Validate email uniqueness when updating (excluding the current user)
     */
    private void validateEmailUniqueness(String email, Long currentUserId) {
        if (email != null && !email.trim().isEmpty()) {
            userRepository.findByEmail(email)
                    .filter(user -> !user.getId().equals(currentUserId))
                    .ifPresent(user -> {
                        log.error("Email already exists: {}", email);
                        throw new UserAlreadyExistsException("Email '" + email + "' already exists");
                    });
        }
    }

    /**
     * Validate phone number uniqueness when updating (excluding the current user)
     */
    private void validatePhoneNumberUniqueness(String phoneNumber, Long currentUserId) {
        if (phoneNumber != null && !phoneNumber.trim().isEmpty()) {
            userRepository.findByPhoneNumber(phoneNumber)
                    .filter(user -> !user.getId().equals(currentUserId))
                    .ifPresent(user -> {
                        log.error("Phone number already exists: {}", phoneNumber);
                        throw new UserAlreadyExistsException("Phone number '" + phoneNumber + "' already exists");
                    });
        }
    }

    /**
     * Validates that the current user has permission to update the target user.
     *
     * Permission hierarchy:
     * - ROOT can update: any user
     * - ADMIN can update: itself, ORG_ADMIN, ORG_USER, DISTRIBUTOR (but not ROOT or other ADMINs)
     * - ORG_ADMIN can update: itself, ORG_USER, DISTRIBUTOR (own organization only)
     */
    private void validateUpdateUserAuthorization(User currentUser, User targetUser) {
        UserRole currentRole = currentUser.getRole();

        // ROOT can update anyone
        if (currentRole == UserRole.ROOT) {
            log.debug("ROOT user authorized to update user ID: {}", targetUser.getId());
            return;
        }

        // Delegate to role-specific validators
        if (currentRole == UserRole.ADMIN) {
            validateAdminUpdateAuthorization(currentUser, targetUser);
            return;
        }

        if (currentRole == UserRole.ORGANIZER_ADMIN) {
            validateOrgAdminUpdateAuthorization(currentUser, targetUser);
            return;
        }

        if (currentRole == UserRole.ORGANIZER_USER || currentRole == UserRole.DISTRIBUTOR) {
            validateSelfUpdateOnly(currentUser, targetUser);
            return;
        }

        // Other roles cannot update users
        log.error("User {} with role {} attempted to update user",
                currentUser.getUsername(), currentRole);
        throw new UnauthorizedAccessException("You do not have permission to update users");
    }

    /**
     * Validates ADMIN user update permissions
     */
    private void validateAdminUpdateAuthorization(User currentUser, User targetUser) {
        // ADMIN can update itself
        if (currentUser.getId().equals(targetUser.getId())) {
            log.debug("ADMIN user updating itself");
            return;
        }

        UserRole targetRole = targetUser.getRole();

        // ADMIN cannot update ROOT
        if (targetRole == UserRole.ROOT) {
            log.error("ADMIN {} attempted to update ROOT user", currentUser.getUsername());
            throw new UnauthorizedAccessException("ADMIN users cannot update ROOT users");
        }

        // ADMIN cannot update other ADMINs
        if (targetRole == UserRole.ADMIN) {
            log.error("ADMIN {} attempted to update another ADMIN user", currentUser.getUsername());
            throw new UnauthorizedAccessException("ADMIN users cannot update other ADMIN users");
        }

        // ADMIN can update ORG_ADMIN, ORG_USER, DISTRIBUTOR
        log.debug("ADMIN user authorized to update user ID: {}", targetUser.getId());
    }

    /**
     * Validates ORG_ADMIN user update permissions
     */
    private void validateOrgAdminUpdateAuthorization(User currentUser, User targetUser) {
        // ORG_ADMIN can update itself
        if (currentUser.getId().equals(targetUser.getId())) {
            log.debug("ORG_ADMIN user updating itself");
            return;
        }

        UserRole targetRole = targetUser.getRole();

        // ORG_ADMIN cannot update ROOT, ADMIN, or other ORG_ADMINs
        if (isRestrictedRole(targetRole, UserRole.ROOT, UserRole.ADMIN, UserRole.ORGANIZER_ADMIN)) {
            log.error("ORG_ADMIN {} attempted to update {} user",
                    currentUser.getUsername(), targetRole);
            throw new UnauthorizedAccessException(
                    "Organization administrators cannot update ROOT, ADMIN, or other ORG_ADMIN users");
        }

        // ORG_ADMIN can only update users within their own organization
        validateSameOrganization(currentUser, targetUser, "update");

        log.debug("ORG_ADMIN user authorized to update user ID: {}", targetUser.getId());
    }

    /**
     * Validates that users can only update themselves
     */
    private void validateSelfUpdateOnly(User currentUser, User targetUser) {
        if (currentUser.getId().equals(targetUser.getId())) {
            log.debug("{} user updating itself", currentUser.getRole());
            return;
        }

        log.error("User {} with role {} attempted to update another user",
                currentUser.getUsername(), currentUser.getRole());
        throw new UnauthorizedAccessException("You can only update your own profile");
    }

    @Override
    @Transactional
    public UserResponse toggleUserEnabled(Long userId, String currentUsername) {
        log.info("Toggling enabled status for user ID: {} by: {}", userId, currentUsername);

        User currentUser = fetchCurrentUser(currentUsername);
        User targetUser = fetchTargetUser(userId);

        validateToggleEnabledAuthorization(currentUser, targetUser);

        // Toggle the enabled status
        boolean newEnabledStatus = !targetUser.getEnabled();
        targetUser.setEnabled(newEnabledStatus);

        User updatedUser = userRepository.save(targetUser);
        log.info("Successfully toggled enabled status for user ID: {} to: {}", userId, newEnabledStatus);

        return UserResponse.fromEntity(updatedUser);
    }

    /**
     * Validates that the current user has permission to toggle the enabled status of the target user.
     *
     * Permission hierarchy:
     * - ROOT can disable: any user
     * - ADMIN can disable: any user
     * - ORG_ADMIN can disable: ORG_USER, DISTRIBUTOR (own organization only)
     * - ORG_USER can disable: DISTRIBUTOR (own organization only)
     */
    private void validateToggleEnabledAuthorization(User currentUser, User targetUser) {
        UserRole currentRole = currentUser.getRole();
        UserRole targetRole = targetUser.getRole();

        // ROOT can disable anyone
        if (currentRole == UserRole.ROOT) {
            log.debug("ROOT user authorized to toggle enabled status for user ID: {}", targetUser.getId());
            return;
        }

        // ADMIN can disable anyone
        if (currentRole == UserRole.ADMIN) {
            log.debug("ADMIN user authorized to toggle enabled status for user ID: {}", targetUser.getId());
            return;
        }

        // ORG_ADMIN restrictions
        if (currentRole == UserRole.ORGANIZER_ADMIN) {
            // ORG_ADMIN cannot disable ROOT, ADMIN, or other ORG_ADMINs
            if (isRestrictedRole(targetRole, UserRole.ROOT, UserRole.ADMIN, UserRole.ORGANIZER_ADMIN)) {
                log.error("ORG_ADMIN {} attempted to toggle enabled status for {} user",
                        currentUser.getUsername(), targetRole);
                throw new UnauthorizedAccessException(
                        "Organization administrators cannot disable ROOT, ADMIN, or other ORG_ADMIN users");
            }

            // ORG_ADMIN can only disable users within their own organization
            validateSameOrganization(currentUser, targetUser, "disable");

            log.debug("ORG_ADMIN user authorized to toggle enabled status for user ID: {}", targetUser.getId());
            return;
        }

        // ORG_USER restrictions
        if (currentRole == UserRole.ORGANIZER_USER) {
            // ORG_USER can only disable DISTRIBUTORs
            if (targetRole != UserRole.DISTRIBUTOR) {
                log.error("ORG_USER {} attempted to toggle enabled status for {} user",
                        currentUser.getUsername(), targetRole);
                throw new UnauthorizedAccessException(
                        "Organization users can only disable DISTRIBUTOR users");
            }

            // ORG_USER can only disable distributors within their own organization
            validateSameOrganization(currentUser, targetUser, "disable");

            log.debug("ORG_USER user authorized to toggle enabled status for distributor ID: {}", targetUser.getId());
            return;
        }

        // Other roles (DISTRIBUTOR) cannot toggle enabled status
        log.error("User {} with role {} attempted to toggle enabled status",
                currentUser.getUsername(), currentRole);
        throw new UnauthorizedAccessException("You do not have permission to toggle user enabled status");
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserById(Long userId, String currentUsername) {
        log.info("Getting user with ID: {} by: {}", userId, currentUsername);

        User currentUser = fetchCurrentUser(currentUsername);
        User targetUser = fetchTargetUser(userId);

        validateGetUserAuthorization(currentUser, targetUser);

        log.info("Successfully retrieved user with ID: {}", userId);
        return UserResponse.fromEntity(targetUser);
    }

    /**
     * Validates that the current user has permission to view the target user.
     *
     * Permission rules:
     * - ROOT can view any user
     * - ADMIN can view any user
     * - ORG_ADMIN can view users in their organization
     * - ORG_USER can view users in their organization
     * - DISTRIBUTOR can view users in their organization
     */
    private void validateGetUserAuthorization(User currentUser, User targetUser) {
        UserRole currentRole = currentUser.getRole();

        // ROOT and ADMIN can view anyone
        if (currentRole == UserRole.ROOT || currentRole == UserRole.ADMIN) {
            log.debug("{} user authorized to view user ID: {}", currentRole, targetUser.getId());
            return;
        }

        // Organization-scoped roles can only view users in their organization
        validateSameOrganization(currentUser, targetUser, "view");

        log.debug("{} user authorized to view user ID: {}", currentRole, targetUser.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers(UserRole role, Long organizationId, Boolean enabled,
                                          Boolean includeDeleted, String search, String sortBy,
                                          String sortDirection, String currentUsername) {
        log.info("Getting all users - role: {}, orgId: {}, enabled: {}, includeDeleted: {}, " +
                 "search: {}, sortBy: {}, sortDir: {} by: {}",
                 role, organizationId, enabled, includeDeleted, search, sortBy,
                 sortDirection, currentUsername);

        User currentUser = fetchCurrentUser(currentUsername);

        // Validate ROOT/ADMIN permission
        validateSystemAdminPermission(currentUser);

        // Fetch users based on filters (organizationId can be null for all orgs)
        List<User> users = fetchFilteredUsers(role, organizationId, enabled, includeDeleted);

        // Apply search filter if provided
        if (search != null && !search.trim().isEmpty()) {
            users = applySearchFilter(users, search);
        }

        // Apply sorting
        users = applySorting(users, sortBy, sortDirection);

        log.info("Successfully retrieved {} users", users.size());
        return users.stream()
                   .map(UserResponse::fromEntity)
                   .toList();
    }

    /**
     * Validates that only ROOT and ADMIN can access system-wide user queries.
     */
    private void validateSystemAdminPermission(User currentUser) {
        UserRole currentRole = currentUser.getRole();
        if (currentRole != UserRole.ROOT && currentRole != UserRole.ADMIN) {
            log.error("User {} with role {} attempted to access system-wide user list",
                    currentUser.getUsername(), currentRole);
            throw new UnauthorizedAccessException(
                    "Only ROOT and ADMIN users can access system-wide user list");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> getOrganizationUsers(UserRole role, Boolean enabled, String search,
                                                   String sortBy, String sortDirection, String currentUsername) {
        log.info("Getting organization users - role: {}, enabled: {}, search: {}, sortBy: {}, " +
                 "sortDir: {} by: {}",
                 role, enabled, search, sortBy, sortDirection, currentUsername);

        User currentUser = fetchCurrentUser(currentUsername);

        // Validate organization user permission
        validateOrganizationUserPermission(currentUser);

        // Get the user's organization ID
        Long organizationId = currentUser.getOrganization().getId();

        // Fetch users in the organization (never include deleted)
        List<User> users = fetchFilteredUsers(role, organizationId, enabled, false);

        // Apply search filter if provided
        if (search != null && !search.trim().isEmpty()) {
            users = applySearchFilter(users, search);
        }

        // Apply sorting
        users = applySorting(users, sortBy, sortDirection);

        log.info("Successfully retrieved {} users from organization {}", users.size(), organizationId);
        return users.stream()
                   .map(UserResponse::fromEntity)
                   .toList();
    }

    /**
     * Validates that only organization users can access organization user list.
     */
    private void validateOrganizationUserPermission(User currentUser) {
        UserRole currentRole = currentUser.getRole();
        if (currentRole != UserRole.ORGANIZER_ADMIN &&
            currentRole != UserRole.ORGANIZER_USER &&
            currentRole != UserRole.DISTRIBUTOR) {
            log.error("User {} with role {} attempted to access organization user list",
                    currentUser.getUsername(), currentRole);
            throw new UnauthorizedAccessException(
                    "Only organization users can access organization user list");
        }

        // Ensure user has an organization
        if (currentUser.getOrganization() == null) {
            log.error("{} user {} has no organization assigned", currentRole, currentUser.getUsername());
            throw new UnauthorizedAccessException(
                    "Organization user must be assigned to an organization");
        }
    }

    /**
     * Fetches users based on filters using appropriate repository methods.
     */
    private List<User> fetchFilteredUsers(UserRole role, Long organizationId, Boolean enabled,
                                          Boolean includeDeleted) {
        boolean shouldIncludeDeleted = Boolean.TRUE.equals(includeDeleted);

        if (organizationId != null && role != null) {
            return fetchUsersByRoleAndOrganization(role, organizationId, shouldIncludeDeleted);
        }

        if (organizationId != null) {
            return fetchUsersByOrganization(organizationId, enabled, shouldIncludeDeleted);
        }

        if (role != null) {
            return fetchUsersByRole(role, shouldIncludeDeleted);
        }

        return fetchAllUsersWithFilters(enabled, shouldIncludeDeleted);
    }

    /**
     * Fetches users by role and organization.
     */
    private List<User> fetchUsersByRoleAndOrganization(UserRole role, Long organizationId,
                                                       boolean includeDeleted) {
        return includeDeleted
                ? userRepository.findByRoleAndOrganizationId(role, organizationId)
                : userRepository.findByRoleAndOrganizationIdAndDeletedFalse(role, organizationId);
    }

    /**
     * Fetches users by organization with optional enabled filter.
     */
    private List<User> fetchUsersByOrganization(Long organizationId, Boolean enabled,
                                                boolean includeDeleted) {
        if (Boolean.TRUE.equals(enabled)) {
            return includeDeleted
                    ? userRepository.findByOrganizationIdAndEnabledTrue(organizationId)
                    : userRepository.findByOrganizationIdAndEnabledTrueAndDeletedFalse(organizationId);
        }

        List<User> orgUsers = includeDeleted
                ? userRepository.findByOrganizationId(organizationId)
                : userRepository.findByOrganizationIdAndDeletedFalse(organizationId);

        return applyEnabledFilter(orgUsers, enabled);
    }

    /**
     * Fetches users by role.
     */
    private List<User> fetchUsersByRole(UserRole role, boolean includeDeleted) {
        return includeDeleted
                ? userRepository.findByRole(role)
                : userRepository.findByRoleAndDeletedFalse(role);
    }

    /**
     * Fetches all users with optional enabled and deleted filters.
     */
    private List<User> fetchAllUsersWithFilters(Boolean enabled, boolean includeDeleted) {
        List<User> allUsers = userRepository.findAll();

        if (!includeDeleted) {
            allUsers = allUsers.stream()
                              .filter(user -> !user.getDeleted())
                              .toList();
        }

        return applyEnabledFilter(allUsers, enabled);
    }

    /**
     * Applies enabled filter to user list if filter is provided.
     */
    private List<User> applyEnabledFilter(List<User> users, Boolean enabled) {
        if (enabled == null) {
            return users;
        }
        return users.stream()
                   .filter(user -> user.getEnabled().equals(enabled))
                   .toList();
    }

    /**
     * Applies case-insensitive search filter on username, email, and fullName.
     */
    private List<User> applySearchFilter(List<User> users, String search) {
        String searchLower = search.toLowerCase().trim();
        return users.stream()
                   .filter(user ->
                       (user.getUsername() != null && user.getUsername().toLowerCase().contains(searchLower)) ||
                       (user.getEmail() != null && user.getEmail().toLowerCase().contains(searchLower)) ||
                       (user.getFullName() != null && user.getFullName().toLowerCase().contains(searchLower))
                   )
                   .toList();
    }

    /**
     * Applies sorting by specified field and direction.
     */
    private List<User> applySorting(List<User> users, String sortBy, String sortDirection) {
        if (sortBy == null || sortBy.trim().isEmpty()) {
            return users; // No sorting
        }

        boolean ascending = !"DESC".equalsIgnoreCase(sortDirection);

        return users.stream()
                   .sorted((u1, u2) -> {
                       int comparison = switch (sortBy.toLowerCase()) {
                           case "username" -> compareNullSafe(u1.getUsername(), u2.getUsername());
                           case "email" -> compareNullSafe(u1.getEmail(), u2.getEmail());
                           case "fullname" -> compareNullSafe(u1.getFullName(), u2.getFullName());
                           case "role" -> u1.getRole().compareTo(u2.getRole());
                           case "createdat" -> u1.getCreatedAt().compareTo(u2.getCreatedAt());
                           default -> 0; // Unknown sort field, no sorting
                       };
                       return ascending ? comparison : -comparison;
                   })
                   .toList();
    }

    /**
     * Null-safe string comparator.
     */
    private int compareNullSafe(String s1, String s2) {
        if (s1 == null && s2 == null) return 0;
        if (s1 == null) return 1;
        if (s2 == null) return -1;
        return s1.compareTo(s2);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(String currentUsername) {
        log.info("Getting current user profile for: {}", currentUsername);

        User currentUser = fetchCurrentUser(currentUsername);

        log.info("Successfully retrieved current user profile for: {}", currentUsername);
        return UserResponse.fromEntity(currentUser);
    }

}
