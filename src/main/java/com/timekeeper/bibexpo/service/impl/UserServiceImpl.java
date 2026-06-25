package com.timekeeper.bibexpo.service.impl;

import com.timekeeper.bibexpo.annotation.Auditable;
import com.timekeeper.bibexpo.aspect.AuditContextHolder;
import com.timekeeper.bibexpo.exception.EventNotFoundException;
import com.timekeeper.bibexpo.exception.InvalidFileException;
import com.timekeeper.bibexpo.exception.InvalidUserDataException;
import com.timekeeper.bibexpo.exception.OrganizationNotFoundException;
import com.timekeeper.bibexpo.exception.UnauthorizedAccessException;
import com.timekeeper.bibexpo.exception.UserAlreadyExistsException;
import com.timekeeper.bibexpo.exception.UserNotFoundException;
import com.timekeeper.bibexpo.model.enums.AuditAction;
import com.timekeeper.bibexpo.model.enums.AuditEntityType;
import com.timekeeper.bibexpo.model.enums.UploadCategory;
import com.timekeeper.bibexpo.model.dto.request.CreateUserRequest;
import com.timekeeper.bibexpo.model.dto.request.UpdateUserRequest;
import com.timekeeper.bibexpo.model.dto.response.PresignUploadResponse;
import com.timekeeper.bibexpo.model.dto.response.UserResponse;
import com.timekeeper.bibexpo.service.StorageService;
import com.timekeeper.bibexpo.model.entity.Event;
import com.timekeeper.bibexpo.model.entity.EventStatus;
import com.timekeeper.bibexpo.model.entity.Organization;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.entity.UserArchive;
import com.timekeeper.bibexpo.model.entity.UserRole;
import com.timekeeper.bibexpo.repository.EventRepository;
import com.timekeeper.bibexpo.repository.OrganizationLimitRepository;
import com.timekeeper.bibexpo.repository.OrganizationRepository;
import com.timekeeper.bibexpo.repository.UserArchiveRepository;
import com.timekeeper.bibexpo.repository.UserRepository;
import com.timekeeper.bibexpo.service.NotificationService;
import com.timekeeper.bibexpo.service.UserService;
import com.timekeeper.bibexpo.service.cache.AuthUserCache;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Implementation of UserService for user management operations
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserArchiveRepository userArchiveRepository;
    private final NotificationService notificationService;
    private final OrganizationRepository organizationRepository;
    private final OrganizationLimitRepository organizationLimitRepository;
    private final EventRepository eventRepository;
    private final PasswordEncoder passwordEncoder;
    private final StorageService storageService;
    private final AuthUserCache authUserCache;

    /**
     * Map a user to a response, presigning a short-lived URL for its profile picture.
     * All read paths go through here so the stored object key is never exposed directly.
     */
    private UserResponse toResponse(User user) {
        UserResponse response = UserResponse.fromEntity(user);
        response.setProfilePictureUrl(storageService.createDownloadUrl(user.getProfilePictureKey()));
        return response;
    }

    @Auditable(entityType = AuditEntityType.USER, action = AuditAction.CREATE)
    @Override
    @Transactional
    public UserResponse createUser(CreateUserRequest request, String currentUsername) {
        log.info("Creating user with username: {} by: {}", request.getUsername(), currentUsername);

        UserRole role = UserRole.valueOf(request.getRole());
        assertCanCreateUser(role, request.getOrganizationId(), request.getEventId(), currentUsername);

        return provisionUser(request, role);
    }

    @Override
    @Transactional(readOnly = true)
    public void assertCanCreateUser(UserRole role, Long organizationId, Long eventId, String currentUsername) {
        User currentUser = fetchCurrentUser(currentUsername);
        validateRootCreationAttempt(role, currentUsername);
        validateCreateUserAuthorization(currentUser, role, organizationId);
        Organization organization = fetchAndValidateOrganization(organizationId, role);
        resolveDistributorEvent(eventId, organization, role);
    }

    @Auditable(entityType = AuditEntityType.USER, action = AuditAction.CREATE)
    @Override
    @Transactional
    public UserResponse createInvitedUser(CreateUserRequest request) {
        log.info("Creating invited user with username: {}", request.getUsername());

        // Authorization was enforced when the invite was issued; the role and organization
        // on the request are the trusted values carried by the invitation, so provision directly.
        UserRole role = UserRole.valueOf(request.getRole());
        return provisionUser(request, role);
    }

    /**
     * Shared creation tail used by both direct and invite-based creation: validates the
     * payload, reserves the organization slot, and persists the user.
     */
    private UserResponse provisionUser(CreateUserRequest request, UserRole role) {
        validateEmailAndPhoneRequirements(request, role);
        validateUniqueness(request);

        Organization organization = fetchAndValidateOrganization(request.getOrganizationId(), role);
        Event event = resolveDistributorEvent(request.getEventId(), organization, role);
        User user = buildUserEntity(request, organization, event, role);
        reserveUserSlot(organization, role);
        User savedUser = userRepository.save(user);

        log.info("Successfully created user with ID: {} and role: {}", savedUser.getId(), savedUser.getRole());
        return toResponse(savedUser);
    }

    @Auditable(entityType = AuditEntityType.USER, action = AuditAction.UPDATE)
    @Override
    @Transactional
    public UserResponse reassignDistributorEvent(Long userId, Long eventId, String currentUsername) {
        log.info("Reassigning distributor ID: {} to event ID: {} by: {}", userId, eventId, currentUsername);

        User currentUser = fetchCurrentUser(currentUsername);
        User targetUser = fetchTargetUser(userId);

        if (targetUser.getRole() != UserRole.DISTRIBUTOR) {
            throw new InvalidUserDataException("Only a distributor can be assigned to an event.");
        }
        validateReassignAuthorization(currentUser, targetUser);

        Event event = resolveDistributorEvent(eventId, targetUser.getOrganization(), UserRole.DISTRIBUTOR);
        targetUser.setEvent(event);
        User saved = userRepository.save(targetUser);
        authUserCache.evict(saved.getUsername());

        log.info("Reassigned distributor ID: {} to event ID: {}", userId, eventId);
        return toResponse(saved);
    }

    /**
     * Authorize a distributor reassignment. ROOT and ADMIN may reassign any distributor;
     * ORGANIZER_ADMIN and ORGANIZER_USER only distributors in their own organization.
     */
    private void validateReassignAuthorization(User currentUser, User targetUser) {
        UserRole currentRole = currentUser.getRole();
        if (currentRole == UserRole.ROOT || currentRole == UserRole.ADMIN) {
            return;
        }
        if (currentRole == UserRole.ORGANIZER_ADMIN || currentRole == UserRole.ORGANIZER_USER) {
            validateSameOrganization(currentUser, targetUser, "reassign");
            return;
        }
        throw new UnauthorizedAccessException("You are not allowed to reassign distributors.");
    }

    /**
     * Fetch the current user who is creating a new user
     */
    private User fetchCurrentUser(String currentUsername) {
        return userRepository.findByUsername(currentUsername)
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
            throw new UnauthorizedAccessException("ROOT users cannot be created.");
        }
    }

    /**
     * Validate email and phone requirements based on role
     */
    private void validateEmailAndPhoneRequirements(CreateUserRequest request, UserRole role) {
        if (role == UserRole.DISTRIBUTOR) {
            return; // Email and phone are optional for distributors
        }

        if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
            log.error("Email is required for role: {}", role);
            throw new InvalidUserDataException("Email is required.");
        }

        if (request.getPhoneNumber() == null || request.getPhoneNumber().trim().isEmpty()) {
            log.error("Phone number is required for role: {}", role);
            throw new InvalidUserDataException("Phone number is required.");
        }
    }

    /**
     * Validate username, email, and phone number uniqueness
     */
    private void validateUniqueness(CreateUserRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            log.error("Username already exists: {}", request.getUsername());
            throw new UserAlreadyExistsException("This username is already taken.");
        }

        if (request.getEmail() != null && !request.getEmail().trim().isEmpty()
                && userRepository.existsByEmail(request.getEmail())) {
            log.error("Email already exists: {}", request.getEmail());
            throw new UserAlreadyExistsException("This email is already registered.");
        }

        if (request.getPhoneNumber() != null && !request.getPhoneNumber().trim().isEmpty()
                && userRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            log.error("Phone number already exists: {}", request.getPhoneNumber());
            throw new UserAlreadyExistsException("This phone number is already registered.");
        }
    }

    /**
     * Fetch and validate organization for organization-scoped roles
     */
    private Organization fetchAndValidateOrganization(Long organizationId, UserRole role) {
        if (!isOrganizationRole(role)) {
            if (isSystemRole(role) && organizationId != null) {
                log.warn("Organization ID provided for system role {}, will be ignored", role);
            }
            return null;
        }

        if (organizationId == null) {
            log.error("Organization ID is required for role: {}", role);
            throw new InvalidUserDataException("Organization is required.");
        }

        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> {
                    log.error("Organization not found with ID: {}", organizationId);
                    return new OrganizationNotFoundException();
                });

        if (Boolean.FALSE.equals(organization.getEnabled())) {
            log.error("Cannot create user for disabled organization ID: {}", organizationId);
            throw new InvalidUserDataException("This organization is currently disabled.");
        }

        return organization;
    }

    /**
     * Resolve and validate the event a distributor is assigned to. The event must belong to the
     * distributor's organization and must not have ended. Returns null for every non-distributor
     * role, since only distributors are event-scoped.
     */
    private Event resolveDistributorEvent(Long eventId, Organization organization, UserRole role) {
        if (role != UserRole.DISTRIBUTOR) {
            return null;
        }
        if (eventId == null) {
            throw new InvalidUserDataException("An event is required for a distributor.");
        }
        Event event = eventRepository.findById(eventId).orElseThrow(EventNotFoundException::new);
        // Events outside the distributor's organization are reported as not found so their
        // existence is not disclosed across organizations.
        if (organization == null || !event.getOrganization().getId().equals(organization.getId())) {
            throw new EventNotFoundException();
        }
        if (isTerminalEvent(event)) {
            throw new InvalidUserDataException("You cannot assign a distributor to a completed or cancelled event.");
        }
        return event;
    }

    private boolean isTerminalEvent(Event event) {
        return event.getStatus() == EventStatus.COMPLETED || event.getStatus() == EventStatus.CANCELLED;
    }

    /**
     * Build user entity from request
     */
    private User buildUserEntity(CreateUserRequest request, Organization organization, Event event, UserRole role) {
        return User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .fullName(request.getFullName())
                .phoneNumber(request.getPhoneNumber())
                .role(role)
                .organization(organization)
                .event(event)
                .enabled(true)
                .accountNonLocked(true)
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

    private static final Set<UserRole> PRIVILEGED_ROLES = EnumSet.of(UserRole.ROOT, UserRole.ADMIN, UserRole.ORGANIZER_ADMIN);

    private static final Map<UserRole, Set<UserRole>> CREATABLE_ROLES = new EnumMap<>(UserRole.class);

    static {
        CREATABLE_ROLES.put(UserRole.ROOT,
                EnumSet.of(UserRole.ADMIN, UserRole.ORGANIZER_ADMIN, UserRole.ORGANIZER_USER, UserRole.DISTRIBUTOR));
        CREATABLE_ROLES.put(UserRole.ADMIN,
                EnumSet.of(UserRole.ORGANIZER_ADMIN, UserRole.ORGANIZER_USER, UserRole.DISTRIBUTOR));
        CREATABLE_ROLES.put(UserRole.ORGANIZER_ADMIN,
                EnumSet.of(UserRole.ORGANIZER_USER, UserRole.DISTRIBUTOR));
        CREATABLE_ROLES.put(UserRole.ORGANIZER_USER,
                EnumSet.of(UserRole.DISTRIBUTOR));
    }

    private void validateCreateUserAuthorization(User currentUser, UserRole requestedRole, Long targetOrganizationId) {
        UserRole currentRole = currentUser.getRole();
        Set<UserRole> allowed = CREATABLE_ROLES.getOrDefault(currentRole, EnumSet.noneOf(UserRole.class));

        if (!allowed.contains(requestedRole)) {
            log.error("User {} with role {} attempted to create {} user",
                    currentUser.getUsername(), currentRole, requestedRole);
            throw new UnauthorizedAccessException("You are not allowed to create users with this role.");
        }

        if (currentRole == UserRole.ORGANIZER_ADMIN || currentRole == UserRole.ORGANIZER_USER) {
            if (currentUser.getOrganization() == null) {
                log.error("{} {} has no organization assigned", currentRole, currentUser.getUsername());
                throw new UnauthorizedAccessException("Your account is not assigned to an organization.");
            }
            if (!currentUser.getOrganization().getId().equals(targetOrganizationId)) {
                log.error("{} {} attempted to create user for org {}. Own org: {}",
                        currentRole, currentUser.getUsername(),
                        targetOrganizationId, currentUser.getOrganization().getId());
                throw new UnauthorizedAccessException("You can only create users in your organization.");
            }
        }

        log.debug("Authorization validated for {} to create role {}", currentUser.getUsername(), requestedRole);
    }

    /**
     * Atomically reserves a usage slot for the new user's role on its organization.
     * The guarded UPDATE increments the matching counter only while it stays within
     * the cap, so concurrent creates cannot overshoot the limit.
     *
     * @param organization the organization to reserve against (null for system roles)
     * @param requestedRole the role being assigned to the new user
     */
    private void reserveUserSlot(Organization organization, UserRole requestedRole) {
        if (organization == null) {
            // System-level roles are not organization-scoped
            return;
        }

        Long orgId = organization.getId();
        boolean reserved;
        String limitMessage;

        switch (requestedRole) {
            case ORGANIZER_ADMIN -> {
                reserved = organizationLimitRepository.tryIncrementAdmins(orgId) > 0;
                limitMessage = "Your organization has reached the maximum number of administrators.";
            }
            case ORGANIZER_USER -> {
                reserved = organizationLimitRepository.tryIncrementOrganizerUsers(orgId) > 0;
                limitMessage = "Your organization has reached the maximum number of organizer users.";
            }
            case DISTRIBUTOR -> {
                reserved = organizationLimitRepository.tryIncrementDistributors(orgId) > 0;
                limitMessage = "Your organization has reached the maximum number of distributors.";
            }
            default -> {
                return;
            }
        }

        if (!reserved) {
            log.error("Organization {} has reached its {} limit", orgId, requestedRole);
            throw new InvalidUserDataException(limitMessage);
        }
    }

    /**
     * Releases the usage slot held by a user's role when the user is removed.
     * The decrement is floored at zero so it can never drive a counter negative.
     *
     * @param organization the organization to release against (null for system roles)
     * @param role the role of the user being removed
     */
    private void releaseUserSlot(Organization organization, UserRole role) {
        if (organization == null) {
            return;
        }

        Long orgId = organization.getId();
        switch (role) {
            case ORGANIZER_ADMIN -> organizationLimitRepository.decrementAdmins(orgId);
            case ORGANIZER_USER -> organizationLimitRepository.decrementOrganizerUsers(orgId);
            case DISTRIBUTOR -> organizationLimitRepository.decrementDistributors(orgId);
            default -> { /* system roles are not organization-scoped */ }
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
        requireOrganization(currentUser);

        if (targetUser.getOrganization() == null ||
            !currentUser.getOrganization().getId().equals(targetUser.getOrganization().getId())) {
            log.error("{} {} attempted to {} user from different organization",
                    currentUser.getRole(), currentUser.getUsername(), action);
            throw new UnauthorizedAccessException("You can only " + action + " users within your organization.");
        }
    }

    private void requireOrganization(User user) {
        if (user.getOrganization() == null) {
            log.error("{} {} has no organization assigned", user.getRole(), user.getUsername());
            throw new UnauthorizedAccessException("Your account is not assigned to an organization.");
        }
    }

    private boolean isSelfUpdate(User currentUser, User targetUser) {
        return currentUser.getId().equals(targetUser.getId());
    }

    private boolean isPrivilegedRole(UserRole role) {
        return PRIVILEGED_ROLES.contains(role);
    }

    @Auditable(entityType = AuditEntityType.USER, action = AuditAction.UPDATE)
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
        authUserCache.evict(updatedUser.getUsername());
        log.info("Successfully updated user with ID: {}", updatedUser.getId());

        return toResponse(updatedUser);
    }

    /**
     * Fetch the target user to be updated
     */
    private User fetchTargetUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.error("User not found with ID: {}", userId);
                    return new UserNotFoundException();
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
                        throw new UserAlreadyExistsException("This email is already registered.");
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
                        throw new UserAlreadyExistsException("This phone number is already registered.");
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

        if (currentRole == UserRole.ORGANIZER_USER) {
            validateOrgUserUpdateAuthorization(currentUser, targetUser);
            return;
        }

        if (currentRole == UserRole.DISTRIBUTOR) {
            validateSelfUpdateOnly(currentUser, targetUser);
            return;
        }

        // Other roles cannot update users
        log.error("User {} with role {} attempted to update user",
                currentUser.getUsername(), currentRole);
        throw new UnauthorizedAccessException("You are not allowed to update users.");
    }

    /**
     * Validates ADMIN user update permissions
     */
    private void validateAdminUpdateAuthorization(User currentUser, User targetUser) {
        if (isSelfUpdate(currentUser, targetUser)) {
            log.debug("ADMIN user updating itself");
            return;
        }

        UserRole targetRole = targetUser.getRole();

        // ADMIN cannot update ROOT
        if (targetRole == UserRole.ROOT) {
            log.error("ADMIN {} attempted to update ROOT user", currentUser.getUsername());
            throw new UnauthorizedAccessException("You cannot update users with equal or higher privileges.");
        }

        // ADMIN cannot update other ADMINs
        if (targetRole == UserRole.ADMIN) {
            log.error("ADMIN {} attempted to update another ADMIN user", currentUser.getUsername());
            throw new UnauthorizedAccessException("You cannot update users with equal or higher privileges.");
        }

        // ADMIN can update ORG_ADMIN, ORG_USER, DISTRIBUTOR
        log.debug("ADMIN user authorized to update user ID: {}", targetUser.getId());
    }

    /**
     * Validates ORG_ADMIN user update permissions
     */
    private void validateOrgAdminUpdateAuthorization(User currentUser, User targetUser) {
        if (isSelfUpdate(currentUser, targetUser)) {
            log.debug("ORG_ADMIN user updating itself");
            return;
        }

        UserRole targetRole = targetUser.getRole();

        // ORG_ADMIN cannot update ROOT, ADMIN, or other ORG_ADMINs
        if (isPrivilegedRole(targetRole)) {
            log.error("ORG_ADMIN {} attempted to update {} user",
                    currentUser.getUsername(), targetRole);
            throw new UnauthorizedAccessException("You cannot update users with equal or higher privileges.");
        }

        // ORG_ADMIN can only update users within their own organization
        validateSameOrganization(currentUser, targetUser, "update");

        log.debug("ORG_ADMIN user authorized to update user ID: {}", targetUser.getId());
    }

    private void validateOrgUserUpdateAuthorization(User currentUser, User targetUser) {
        if (isSelfUpdate(currentUser, targetUser)) {
            log.debug("ORG_USER user updating itself");
            return;
        }

        if (targetUser.getRole() != UserRole.DISTRIBUTOR) {
            log.error("ORG_USER {} attempted to update {} user",
                    currentUser.getUsername(), targetUser.getRole());
            throw new UnauthorizedAccessException("You can only update distributor accounts.");
        }

        validateSameOrganization(currentUser, targetUser, "update");
        log.debug("ORG_USER user authorized to update distributor ID: {}", targetUser.getId());
    }

    /**
     * Validates that users can only update themselves
     */
    private void validateSelfUpdateOnly(User currentUser, User targetUser) {
        if (isSelfUpdate(currentUser, targetUser)) {
            log.debug("{} user updating itself", currentUser.getRole());
            return;
        }

        log.error("User {} with role {} attempted to update another user",
                currentUser.getUsername(), currentUser.getRole());
        throw new UnauthorizedAccessException("You can only update your own profile");
    }

    @Auditable(entityType = AuditEntityType.USER, action = AuditAction.STATUS_CHANGE)
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
        authUserCache.evict(updatedUser.getUsername());
        log.info("Successfully toggled enabled status for user ID: {} to: {}", userId, newEnabledStatus);

        return toResponse(updatedUser);
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
            if (isPrivilegedRole(targetRole)) {
                log.error("ORG_ADMIN {} attempted to toggle enabled status for {} user",
                        currentUser.getUsername(), targetRole);
                throw new UnauthorizedAccessException(
                        "You cannot enable or disable users with equal or higher privileges.");
            }

            // ORG_ADMIN can only disable users within their own organization
            validateSameOrganization(currentUser, targetUser, "disable");

            log.debug("ORG_ADMIN user authorized to toggle enabled status for user ID: {}", targetUser.getId());
            return;
        }

        // ORG_USER restrictions
        if (currentRole == UserRole.ORGANIZER_USER) {
            if (isSelfUpdate(currentUser, targetUser)) {
                log.debug("ORG_USER user toggling own enabled status");
                return;
            }

            if (targetRole != UserRole.DISTRIBUTOR) {
                log.error("ORG_USER {} attempted to toggle enabled status for {} user",
                        currentUser.getUsername(), targetRole);
                throw new UnauthorizedAccessException(
                        "You can only enable or disable distributor accounts.");
            }

            validateSameOrganization(currentUser, targetUser, "disable");

            log.debug("ORG_USER user authorized to toggle enabled status for distributor ID: {}", targetUser.getId());
            return;
        }

        // Other roles (DISTRIBUTOR) cannot toggle enabled status
        log.error("User {} with role {} attempted to toggle enabled status",
                currentUser.getUsername(), currentRole);
        throw new UnauthorizedAccessException("You are not allowed to enable or disable users.");
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserById(Long userId, String currentUsername) {
        log.info("Getting user with ID: {} by: {}", userId, currentUsername);

        User currentUser = fetchCurrentUser(currentUsername);
        User targetUser = fetchTargetUser(userId);

        // A target the caller may not view is reported as not found, so its existence
        // is not disclosed across organizations or privilege levels.
        if (!canViewUser(currentUser, targetUser)) {
            log.warn("{} {} not permitted to view user ID {}; reporting as not found",
                    currentUser.getRole(), currentUsername, userId);
            throw new UserNotFoundException();
        }

        log.info("Successfully retrieved user with ID: {}", userId);
        return toResponse(targetUser);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserByUsername(String username, String currentUsername) {
        log.info("Getting user with username: {} by: {}", username, currentUsername);

        User currentUser = fetchCurrentUser(currentUsername);
        User targetUser = fetchTargetUserByUsername(username);

        // A target the caller may not view is reported as not found, so that a username
        // belonging to a privileged or cross-organization account cannot be distinguished
        // from one that does not exist (no account-existence disclosure by username).
        if (!canViewUser(currentUser, targetUser)) {
            log.warn("{} {} not permitted to view user '{}'; reporting as not found",
                    currentUser.getRole(), currentUsername, username);
            throw new UserNotFoundException();
        }

        log.info("Successfully retrieved user with username: {}", username);
        return toResponse(targetUser);
    }

    /**
     * Fetch the target user by username
     */
    private User fetchTargetUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    log.error("User not found with username: {}", username);
                    return new UserNotFoundException();
                });
    }

    /**
     * Whether {@code currentUser} is permitted to view {@code targetUser}: ROOT and ADMIN may
     * view anyone, organization-scoped roles only users within their own organization.
     */
    private boolean canViewUser(User currentUser, User targetUser) {
        UserRole currentRole = currentUser.getRole();
        if (currentRole == UserRole.ROOT || currentRole == UserRole.ADMIN) {
            return true;
        }
        return currentUser.getOrganization() != null
                && targetUser.getOrganization() != null
                && currentUser.getOrganization().getId().equals(targetUser.getOrganization().getId());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserResponse> getUsers(UserRole role, Long organizationId, Long eventId, Boolean enabled,
                                       String search, Pageable pageable,
                                       String currentUsername) {
        log.info("Getting users - role: {}, orgId: {}, eventId: {}, enabled: {}, search: {} by: {}",
                role, organizationId, eventId, enabled, search, currentUsername);

        User currentUser = fetchCurrentUser(currentUsername);
        UserRole currentRole = currentUser.getRole();

        Long scopedOrgId = organizationId;

        if (currentRole == UserRole.ORGANIZER_ADMIN || currentRole == UserRole.ORGANIZER_USER) {
            requireOrganization(currentUser);
            scopedOrgId = currentUser.getOrganization().getId();
        }

        Specification<User> spec = buildUserSpecification(role, scopedOrgId, eventId, enabled, search);
        Page<UserResponse> responsePage = userRepository.findAll(spec, pageable).map(this::toResponse);

        log.info("Successfully retrieved {} users (page {} of {})",
                responsePage.getNumberOfElements(), responsePage.getNumber() + 1, responsePage.getTotalPages());
        return responsePage;
    }

    private Specification<User> buildUserSpecification(
            UserRole role, Long organizationId, Long eventId, Boolean enabled, String search) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (role != null) {
                predicates.add(cb.equal(root.get("role"), role));
            }
            if (organizationId != null) {
                predicates.add(cb.equal(root.get("organization").get("id"), organizationId));
            }
            if (eventId != null) {
                predicates.add(cb.equal(root.get("event").get("id"), eventId));
            }
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("username")), pattern),
                        cb.like(cb.lower(root.get("email")), pattern),
                        cb.like(cb.lower(root.get("fullName")), pattern),
                        cb.like(cb.lower(root.get("phoneNumber")), pattern)
                ));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(String currentUsername) {
        log.info("Getting current user profile for: {}", currentUsername);

        User currentUser = fetchCurrentUser(currentUsername);

        log.info("Successfully retrieved current user profile for: {}", currentUsername);
        return toResponse(currentUser);
    }

    @Auditable(entityType = AuditEntityType.USER, action = AuditAction.DELETE)
    @Override
    @Transactional
    public void deleteUser(Long userId, String currentUsername) {
        log.info("Archiving user ID: {} by: {}", userId, currentUsername);

        User currentUser = fetchCurrentUser(currentUsername);
        User targetUser = fetchTargetUser(userId);

        validateDeleteUserAuthorization(currentUser, targetUser);

        UserArchive archive = buildArchiveFromUser(targetUser, currentUsername);
        userArchiveRepository.save(archive);

        int notificationsDeleted = notificationService.deleteAllForUser(targetUser.getId());
        log.debug("Deleted {} notifications for user ID: {}", notificationsDeleted, userId);

        Organization organization = targetUser.getOrganization();
        UserRole role = targetUser.getRole();

        String label = (targetUser.getFullName() != null && !targetUser.getFullName().isBlank())
                ? targetUser.getFullName() : targetUser.getUsername();
        AuditContextHolder.setEntityLabel(label);
        AuditContextHolder.setOrganizationId(organization != null ? organization.getId() : null);

        String pictureKey = targetUser.getProfilePictureKey();
        String username = targetUser.getUsername();
        userRepository.delete(targetUser);
        releaseUserSlot(organization, role);
        authUserCache.evict(username);
        deleteQuietly(pictureKey);

        log.info("Successfully archived user ID: {} (username: {})", userId, username);
    }

    @Override
    @Transactional
    public int purgeUsersForOrganization(Long organizationId) {
        List<User> users = userRepository.findByOrganizationId(organizationId);
        for (User user : users) {
            notificationService.deleteAllForUser(user.getId());
            authUserCache.evict(user.getUsername());
            deleteQuietly(user.getProfilePictureKey());
        }
        userRepository.deleteAll(users);
        // Archived (former) users still FK the organization, so drop those rows before it is removed.
        userArchiveRepository.deleteByOrganizationId(organizationId);
        userRepository.flush();
        log.info("Purged {} users for organization ID: {}", users.size(), organizationId);
        return users.size();
    }

    /**
     * Validates that the current user has permission to archive the target user.
     * ROOT users can never be archived.
     */
    private void validateDeleteUserAuthorization(User currentUser, User targetUser) {
        if (targetUser.getRole() == UserRole.ROOT) {
            log.error("Attempt to archive ROOT user by: {}", currentUser.getUsername());
            throw new UnauthorizedAccessException("ROOT users cannot be archived.");
        }

        if (isSelfUpdate(currentUser, targetUser)) {
            log.error("User {} attempted to archive itself", currentUser.getUsername());
            throw new UnauthorizedAccessException("You cannot archive your own account.");
        }

        UserRole currentRole = currentUser.getRole();

        if (currentRole == UserRole.ROOT) {
            return;
        }

        if (currentRole == UserRole.ADMIN) {
            if (targetUser.getRole() == UserRole.ADMIN) {
                throw new UnauthorizedAccessException("You cannot archive users with equal or higher privileges.");
            }
            return;
        }

        if (currentRole == UserRole.ORGANIZER_ADMIN) {
            if (isPrivilegedRole(targetUser.getRole())) {
                throw new UnauthorizedAccessException("You cannot archive users with equal or higher privileges.");
            }
            validateSameOrganization(currentUser, targetUser, "archive");
            return;
        }

        if (currentRole == UserRole.ORGANIZER_USER) {
            if (targetUser.getRole() != UserRole.DISTRIBUTOR) {
                log.error("ORG_USER {} attempted to archive {} user",
                        currentUser.getUsername(), targetUser.getRole());
                throw new UnauthorizedAccessException("You can only archive distributor accounts.");
            }
            validateSameOrganization(currentUser, targetUser, "archive");
            return;
        }

        log.error("User {} with role {} attempted to archive a user",
                currentUser.getUsername(), currentRole);
        throw new UnauthorizedAccessException("You are not allowed to archive users.");
    }

    private UserArchive buildArchiveFromUser(User user, String archivedBy) {
        return UserArchive.builder()
                .id(user.getId())
                .username(user.getUsername())
                .password(user.getPassword())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phoneNumber(user.getPhoneNumber())
                .role(user.getRole())
                .organization(user.getOrganization())
                .accountNonLocked(user.getAccountNonLocked())
                .enabled(user.getEnabled())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .createdBy(user.getCreatedBy())
                .lastModifiedBy(user.getLastModifiedBy())
                .archivedAt(Instant.now())
                .archivedBy(archivedBy)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PresignUploadResponse createProfilePictureUploadUrl(Long userId, String contentType, String currentUsername) {
        User currentUser = fetchCurrentUser(currentUsername);
        User targetUser = fetchTargetUser(userId);
        validateUpdateUserAuthorization(currentUser, targetUser);
        return storageService.createUploadUrl(UploadCategory.PROFILE_PICTURE, targetUser.getId(), contentType);
    }

    @Override
    @Transactional
    public UserResponse attachProfilePicture(Long userId, String objectKey, String currentUsername) {
        log.info("Attaching profile picture for user ID: {} by: {}", userId, currentUsername);
        User currentUser = fetchCurrentUser(currentUsername);
        User targetUser = fetchTargetUser(userId);
        validateUpdateUserAuthorization(currentUser, targetUser);

        if (UploadCategory.PROFILE_PICTURE.ownsKey(targetUser.getId(), objectKey)) {
            throw new InvalidFileException("This upload does not belong to this profile.");
        }
        if (!storageService.objectExists(objectKey)) {
            throw new InvalidFileException("The uploaded file could not be found.");
        }

        String previousKey = targetUser.getProfilePictureKey();
        targetUser.setProfilePictureKey(objectKey);
        User saved = userRepository.saveAndFlush(targetUser);
        authUserCache.evict(saved.getUsername());
        if (previousKey != null && !previousKey.equals(objectKey)) {
            deleteQuietly(previousKey);
        }
        log.info("Successfully attached profile picture for user ID: {}", userId);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public UserResponse removeProfilePicture(Long userId, String currentUsername) {
        log.info("Removing profile picture for user ID: {} by: {}", userId, currentUsername);
        User currentUser = fetchCurrentUser(currentUsername);
        User targetUser = fetchTargetUser(userId);
        validateUpdateUserAuthorization(currentUser, targetUser);

        String previousKey = targetUser.getProfilePictureKey();
        targetUser.setProfilePictureKey(null);
        User saved = userRepository.saveAndFlush(targetUser);
        authUserCache.evict(saved.getUsername());
        deleteQuietly(previousKey);
        log.info("Successfully removed profile picture for user ID: {}", userId);
        return toResponse(saved);
    }

    /** Best-effort object deletion: orphaned objects are tolerable, a failed cleanup must not roll back the entity change. */
    private void deleteQuietly(String objectKey) {
        try {
            storageService.delete(objectKey);
        } catch (Exception e) {
            log.warn("Failed to delete object {}: {}", objectKey, e.getMessage());
        }
    }

}
