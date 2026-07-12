package com.timekeeper.bibexpo.service.impl;

import com.timekeeper.bibexpo.annotation.Auditable;
import com.timekeeper.bibexpo.aspect.AuditContextHolder;
import com.timekeeper.bibexpo.exception.EventNotFoundException;
import com.timekeeper.bibexpo.exception.InvalidUserDataException;
import com.timekeeper.bibexpo.exception.OrganizationNotFoundException;
import com.timekeeper.bibexpo.exception.UserAlreadyExistsException;
import com.timekeeper.bibexpo.exception.UserNotFoundException;
import com.timekeeper.bibexpo.model.enums.AuditAction;
import com.timekeeper.bibexpo.model.enums.AuditEntityType;
import com.timekeeper.bibexpo.model.dto.request.ChangePasswordRequest;
import com.timekeeper.bibexpo.model.dto.request.CreateUserRequest;
import com.timekeeper.bibexpo.model.dto.request.UpdateUserRequest;
import com.timekeeper.bibexpo.model.dto.response.UserResponse;
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
import com.timekeeper.bibexpo.notification.service.NotificationService;
import com.timekeeper.bibexpo.security.CurrentActor;
import com.timekeeper.bibexpo.service.UserProfileMediaService;
import com.timekeeper.bibexpo.service.UserService;
import com.timekeeper.bibexpo.service.cache.AuthUserCache;
import com.timekeeper.bibexpo.service.util.UserResponseMapper;
import com.timekeeper.bibexpo.service.validator.UserAccessPolicy;
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
import java.util.List;

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
    private final AuthUserCache authUserCache;
    private final UserAccessPolicy accessPolicy;
    private final UserResponseMapper responseMapper;
    private final UserProfileMediaService profileMediaService;

    @Auditable(entityType = AuditEntityType.USER, action = AuditAction.CREATE)
    @Override
    @Transactional
    public UserResponse createUser(CreateUserRequest request, CurrentActor actor) {
        log.info("Creating user with username: {} by: {}", request.getUsername(), actor.username());

        UserRole role = UserRole.valueOf(request.getRole());
        assertCanCreateUser(role, request.getOrganizationId(), request.getEventId(), actor);

        return provisionUser(request, role);
    }

    @Override
    @Transactional(readOnly = true)
    public void assertCanCreateUser(UserRole role, Long organizationId, Long eventId, CurrentActor actor) {
        accessPolicy.validateRootCreationAttempt(role, actor.username());
        accessPolicy.validateCreateUserAuthorization(actor, role, organizationId);
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
        return responseMapper.toResponse(savedUser);
    }

    @Auditable(entityType = AuditEntityType.USER, action = AuditAction.UPDATE)
    @Override
    @Transactional
    public UserResponse reassignDistributorEvent(Long userId, Long eventId, CurrentActor actor) {
        log.info("Reassigning distributor ID: {} to event ID: {} by: {}", userId, eventId, actor.username());

        User targetUser = fetchTargetUser(userId);

        if (targetUser.getRole() != UserRole.DISTRIBUTOR) {
            throw new InvalidUserDataException("Only a distributor can be assigned to an event.");
        }
        accessPolicy.validateReassignAuthorization(actor, targetUser);

        Event event = resolveDistributorEvent(eventId, targetUser.getOrganization(), UserRole.DISTRIBUTOR);
        targetUser.setEvent(event);
        User saved = userRepository.save(targetUser);
        authUserCache.evict(saved.getUsername());

        log.info("Reassigned distributor ID: {} to event ID: {}", userId, eventId);
        return responseMapper.toResponse(saved);
    }

    /**
     * Fetch the caller's own entity for the few operations that need more than the
     * {@link CurrentActor} view (password verification, fresh own-profile read).
     */
    private User fetchCurrentUser(String currentUsername) {
        return userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> {
                    log.error("Current user not found: {}", currentUsername);
                    return new InvalidUserDataException("Current user not found: " + currentUsername);
                });
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

    @Auditable(entityType = AuditEntityType.USER, action = AuditAction.UPDATE)
    @Override
    @Transactional
    public UserResponse updateUser(Long userId, UpdateUserRequest request, CurrentActor actor) {
        log.info("Updating user with ID: {} by: {}", userId, actor.username());

        User targetUser = fetchTargetUser(userId);

        accessPolicy.validateUpdateUserAuthorization(actor, targetUser);

        // Update fields if provided
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

        return responseMapper.toResponse(updatedUser);
    }

    @Override
    @Transactional
    public void changeOwnPassword(CurrentActor actor, ChangePasswordRequest request) {
        log.info("Password change requested by: {}", actor.username());

        User currentUser = fetchCurrentUser(actor.username());

        if (!passwordEncoder.matches(request.getCurrentPassword(), currentUser.getPassword())) {
            log.warn("Password change rejected for {}: current password did not match", actor.username());
            throw new InvalidUserDataException("Your current password is incorrect.");
        }
        if (passwordEncoder.matches(request.getNewPassword(), currentUser.getPassword())) {
            throw new InvalidUserDataException("Your new password must be different from your current password.");
        }

        currentUser.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(currentUser);
        authUserCache.evict(currentUser.getUsername());
        log.info("Password changed for user ID: {}", currentUser.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public void assertCanUpdateUser(Long userId, CurrentActor actor) {
        User targetUser = fetchTargetUser(userId);
        accessPolicy.validateUpdateUserAuthorization(actor, targetUser);
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

    @Auditable(entityType = AuditEntityType.USER, action = AuditAction.STATUS_CHANGE)
    @Override
    @Transactional
    public UserResponse toggleUserEnabled(Long userId, CurrentActor actor) {
        log.info("Toggling enabled status for user ID: {} by: {}", userId, actor.username());

        User targetUser = fetchTargetUser(userId);

        accessPolicy.validateToggleEnabledAuthorization(actor, targetUser);

        // Toggle the enabled status
        boolean newEnabledStatus = !targetUser.getEnabled();
        targetUser.setEnabled(newEnabledStatus);

        User updatedUser = userRepository.save(targetUser);
        authUserCache.evict(updatedUser.getUsername());
        log.info("Successfully toggled enabled status for user ID: {} to: {}", userId, newEnabledStatus);

        return responseMapper.toResponse(updatedUser);
    }

    @Auditable(entityType = AuditEntityType.USER, action = AuditAction.STATUS_CHANGE)
    @Override
    @Transactional
    public UserResponse toggleUserLocked(Long userId, CurrentActor actor) {
        log.info("Toggling locked status for user ID: {} by: {}", userId, actor.username());

        User targetUser = fetchTargetUser(userId);

        accessPolicy.validateToggleLockedAuthorization(actor, targetUser);

        boolean newNonLockedStatus = !targetUser.getAccountNonLocked();
        targetUser.setAccountNonLocked(newNonLockedStatus);

        User updatedUser = userRepository.save(targetUser);
        authUserCache.evict(updatedUser.getUsername());
        log.info("Successfully toggled locked status for user ID: {} to accountNonLocked: {}", userId, newNonLockedStatus);

        return responseMapper.toResponse(updatedUser);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserById(Long userId, CurrentActor actor) {
        log.info("Getting user with ID: {} by: {}", userId, actor.username());

        User targetUser = fetchTargetUser(userId);

        // A target the caller may not view is reported as not found, so its existence
        // is not disclosed across organizations or privilege levels.
        if (!accessPolicy.canViewUser(actor, targetUser)) {
            log.warn("{} {} not permitted to view user ID {}; reporting as not found",
                    actor.role(), actor.username(), userId);
            throw new UserNotFoundException();
        }

        log.info("Successfully retrieved user with ID: {}", userId);
        return responseMapper.toResponse(targetUser);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserByUsername(String username, CurrentActor actor) {
        log.info("Getting user with username: {} by: {}", username, actor.username());

        User targetUser = fetchTargetUserByUsername(username);

        // A target the caller may not view is reported as not found, so that a username
        // belonging to a privileged or cross-organization account cannot be distinguished
        // from one that does not exist (no account-existence disclosure by username).
        if (!accessPolicy.canViewUser(actor, targetUser)) {
            log.warn("{} {} not permitted to view user '{}'; reporting as not found",
                    actor.role(), actor.username(), username);
            throw new UserNotFoundException();
        }

        log.info("Successfully retrieved user with username: {}", username);
        return responseMapper.toResponse(targetUser);
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

    @Override
    @Transactional(readOnly = true)
    public Page<UserResponse> getUsers(UserRole role, Long organizationId, Long eventId, Boolean enabled,
                                       String search, Pageable pageable,
                                       CurrentActor actor) {
        log.info("Getting users - role: {}, orgId: {}, eventId: {}, enabled: {}, search: {} by: {}",
                role, organizationId, eventId, enabled, search, actor.username());

        UserRole currentRole = actor.role();

        Long scopedOrgId = organizationId;

        if (currentRole == UserRole.ORGANIZER_ADMIN || currentRole == UserRole.ORGANIZER_USER) {
            accessPolicy.requireOrganization(actor);
            scopedOrgId = actor.organizationId();
        }

        Specification<User> spec = buildUserSpecification(role, scopedOrgId, eventId, enabled, search);
        Page<UserResponse> responsePage = userRepository.findAll(spec, pageable).map(responseMapper::toResponse);

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
    public UserResponse getCurrentUser(CurrentActor actor) {
        log.info("Getting current user profile for: {}", actor.username());

        User currentUser = fetchCurrentUser(actor.username());

        log.info("Successfully retrieved current user profile for: {}", actor.username());
        return responseMapper.toResponse(currentUser);
    }

    @Auditable(entityType = AuditEntityType.USER, action = AuditAction.DELETE)
    @Override
    @Transactional
    public void deleteUser(Long userId, CurrentActor actor) {
        log.info("Archiving user ID: {} by: {}", userId, actor.username());

        User targetUser = fetchTargetUser(userId);

        accessPolicy.validateDeleteUserAuthorization(actor, targetUser);

        UserArchive archive = buildArchiveFromUser(targetUser, actor.username());
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
        profileMediaService.deletePictureQuietly(pictureKey);

        log.info("Successfully archived user ID: {} (username: {})", userId, username);
    }

    @Override
    @Transactional
    public int purgeUsersForOrganization(Long organizationId) {
        List<User> users = userRepository.findByOrganizationId(organizationId);
        for (User user : users) {
            notificationService.deleteAllForUser(user.getId());
            authUserCache.evict(user.getUsername());
            profileMediaService.deletePictureQuietly(user.getProfilePictureKey());
        }
        userRepository.deleteAll(users);
        // Archived (former) users still FK the organization, so drop those rows before it is removed.
        userArchiveRepository.deleteByOrganizationId(organizationId);
        userRepository.flush();
        log.info("Purged {} users for organization ID: {}", users.size(), organizationId);
        return users.size();
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

}
