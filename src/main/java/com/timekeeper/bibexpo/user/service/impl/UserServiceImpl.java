package com.timekeeper.bibexpo.user.service.impl;

import com.timekeeper.bibexpo.audit.api.Auditable;
import com.timekeeper.bibexpo.audit.api.AuditAction;
import com.timekeeper.bibexpo.audit.api.AuditContextHolder;
import com.timekeeper.bibexpo.audit.api.AuditEntityType;
import com.timekeeper.bibexpo.event.exception.EventNotFoundException;
import com.timekeeper.bibexpo.event.model.entity.Event;
import com.timekeeper.bibexpo.event.model.entity.EventStatus;
import com.timekeeper.bibexpo.notification.service.NotificationService;
import com.timekeeper.bibexpo.organization.api.OrganizationDirectory;
import com.timekeeper.bibexpo.organization.api.OrganizationSeatQuota;
import com.timekeeper.bibexpo.organization.model.entity.Organization;
import com.timekeeper.bibexpo.event.api.EventStore;
import com.timekeeper.bibexpo.shared.error.InvalidUserDataException;
import com.timekeeper.bibexpo.user.api.CurrentActor;
import com.timekeeper.bibexpo.shared.security.UserRole;
import com.timekeeper.bibexpo.user.exception.UserAlreadyExistsException;
import com.timekeeper.bibexpo.user.exception.UserNotFoundException;
import com.timekeeper.bibexpo.user.model.dto.request.ChangePasswordRequest;
import com.timekeeper.bibexpo.user.model.dto.request.CreateUserRequest;
import com.timekeeper.bibexpo.user.model.dto.request.UpdateUserRequest;
import com.timekeeper.bibexpo.user.model.dto.response.UserResponse;
import com.timekeeper.bibexpo.user.model.entity.User;
import com.timekeeper.bibexpo.user.model.event.PasswordChangedEvent;
import com.timekeeper.bibexpo.user.model.entity.UserArchive;
import com.timekeeper.bibexpo.user.repository.UserArchiveRepository;
import com.timekeeper.bibexpo.user.repository.UserRepository;
import com.timekeeper.bibexpo.user.service.cache.AuthUserCache;
import com.timekeeper.bibexpo.user.service.UserProfileMediaService;
import com.timekeeper.bibexpo.user.service.UserService;
import com.timekeeper.bibexpo.user.service.util.UserResponseMapper;
import com.timekeeper.bibexpo.user.service.validator.UserAccessPolicy;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
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
    private final UserArchiveRepository userArchiveRepository;
    private final NotificationService notificationService;
    private final OrganizationDirectory organizationDirectory;
    private final OrganizationSeatQuota seatQuota;
    private final EventStore eventStore;
    private final PasswordEncoder passwordEncoder;
    private final AuthUserCache authUserCache;
    private final UserAccessPolicy accessPolicy;
    private final UserResponseMapper responseMapper;
    private final UserProfileMediaService profileMediaService;
    private final ApplicationEventPublisher eventPublisher;

    @Auditable(entityType = AuditEntityType.USER, action = AuditAction.CREATE)
    @Override
    @Transactional
    public UserResponse createUser(CreateUserRequest request, CurrentActor actor) {
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

        log.atInfo()
                .addKeyValue("event", "user.created")
                .addKeyValue("targetUserId", savedUser.getId())
                .addKeyValue("targetRole", savedUser.getRole())
                .addKeyValue("targetOrgId", organization != null ? organization.getId() : null)
                .log("User created");
        return responseMapper.toResponse(savedUser);
    }

    @Auditable(entityType = AuditEntityType.USER, action = AuditAction.UPDATE)
    @Override
    @Transactional
    public UserResponse reassignDistributorEvent(Long userId, Long eventId, CurrentActor actor) {
        User targetUser = fetchTargetUser(userId);

        if (targetUser.getRole() != UserRole.DISTRIBUTOR) {
            throw new InvalidUserDataException("Only a distributor can be assigned to an event.");
        }
        accessPolicy.validateReassignAuthorization(actor, targetUser);

        Event event = resolveDistributorEvent(eventId, targetUser.getOrganization(), UserRole.DISTRIBUTOR);
        targetUser.setEvent(event);
        User saved = userRepository.save(targetUser);
        authUserCache.evict(saved.getUsername());

        log.atInfo()
                .addKeyValue("event", "user.distributor.reassigned")
                .addKeyValue("targetUserId", userId)
                .addKeyValue("eventId", eventId)
                .log("Distributor reassigned");
        return responseMapper.toResponse(saved);
    }

    /**
     * Fetch the caller's own entity for the few operations that need more than the
     * {@link CurrentActor} view (password verification, fresh own-profile read).
     */
    private User fetchCurrentUser(String currentUsername) {
        return userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> {
                    log.atError().addKeyValue("event", "user.current.missing")
                            .addKeyValue("username", currentUsername)
                            .log("Authenticated user has no account row");
                    return new InvalidUserDataException("Your account could not be found.");
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
            throw new InvalidUserDataException("Email is required.");
        }

        if (request.getPhoneNumber() == null || request.getPhoneNumber().trim().isEmpty()) {
            throw new InvalidUserDataException("Phone number is required.");
        }
    }

    /**
     * Validate username, email, and phone number uniqueness
     */
    private void validateUniqueness(CreateUserRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new UserAlreadyExistsException("This username is already taken.");
        }

        if (request.getEmail() != null && !request.getEmail().trim().isEmpty()
                && userRepository.existsByEmail(request.getEmail())) {
            throw new UserAlreadyExistsException("This email is already registered.");
        }

        if (request.getPhoneNumber() != null && !request.getPhoneNumber().trim().isEmpty()
                && userRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            throw new UserAlreadyExistsException("This phone number is already registered.");
        }
    }

    /**
     * Fetch and validate organization for organization-scoped roles
     */
    private Organization fetchAndValidateOrganization(Long organizationId, UserRole role) {
        if (!isOrganizationRole(role)) {
            if (isSystemRole(role) && organizationId != null) {
                log.debug("Organization ID ignored for system role {}", role);
            }
            return null;
        }

        if (organizationId == null) {
            throw new InvalidUserDataException("Organization is required.");
        }

        Organization organization = organizationDirectory.requireById(organizationId);

        if (Boolean.FALSE.equals(organization.getEnabled())) {
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
        Event event = eventStore.requireById(eventId);
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
        seatQuota.reserveSeat(organization.getId(), requestedRole);
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
        seatQuota.releaseSeat(organization.getId(), role);
    }

    @Auditable(entityType = AuditEntityType.USER, action = AuditAction.UPDATE)
    @Override
    @Transactional
    public UserResponse updateUser(Long userId, UpdateUserRequest request, CurrentActor actor) {
        User targetUser = fetchTargetUser(userId);

        accessPolicy.validateUpdateUserAuthorization(actor, targetUser);

        List<String> changedFields = new ArrayList<>();
        if (request.getEmail() != null) {
            validateEmailUniqueness(request.getEmail(), targetUser.getId());
            targetUser.setEmail(request.getEmail());
            changedFields.add("email");
        }

        if (request.getFullName() != null) {
            targetUser.setFullName(request.getFullName());
            changedFields.add("fullName");
        }

        if (request.getPhoneNumber() != null) {
            validatePhoneNumberUniqueness(request.getPhoneNumber(), targetUser.getId());
            targetUser.setPhoneNumber(request.getPhoneNumber());
            changedFields.add("phoneNumber");
        }

        User updatedUser = userRepository.save(targetUser);
        authUserCache.evict(updatedUser.getUsername());
        log.atInfo()
                .addKeyValue("event", "user.updated")
                .addKeyValue("targetUserId", updatedUser.getId())
                .addKeyValue("changedFields", changedFields)
                .log("User updated");

        return responseMapper.toResponse(updatedUser);
    }

    @Override
    @Transactional
    public void changeOwnPassword(CurrentActor actor, ChangePasswordRequest request) {
        User currentUser = fetchCurrentUser(actor.username());

        if (!passwordEncoder.matches(request.getCurrentPassword(), currentUser.getPassword())) {
            log.atWarn().addKeyValue("event", "user.password.change_rejected")
                    .addKeyValue("reason", "wrong_current_password")
                    .log("Password change rejected");
            throw new InvalidUserDataException("Your current password is incorrect.");
        }
        if (passwordEncoder.matches(request.getNewPassword(), currentUser.getPassword())) {
            throw new InvalidUserDataException("Your new password must be different from your current password.");
        }

        storeNewPassword(currentUser, request.getNewPassword());
    }

    @Override
    @Transactional
    public void applyNewPassword(Long userId, String rawPassword) {
        storeNewPassword(fetchTargetUser(userId), rawPassword);
    }

    /**
     * The four steps every password change shares: encode, persist, drop the stale principal so the
     * old password cannot keep authenticating from the cache, and announce the change so identity
     * can sign out the devices still holding a session from the old one.
     */
    private void storeNewPassword(User user, String rawPassword) {
        user.setPassword(passwordEncoder.encode(rawPassword));
        userRepository.save(user);
        authUserCache.evict(user.getUsername());
        eventPublisher.publishEvent(new PasswordChangedEvent(user.getUsername()));
        log.atInfo().addKeyValue("event", "user.password.changed").addKeyValue("targetUserId", user.getId())
                .log("Password changed");
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
        return userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
    }

    /**
     * Validate email uniqueness when updating (excluding the current user)
     */
    private void validateEmailUniqueness(String email, Long currentUserId) {
        if (email != null && !email.trim().isEmpty()) {
            userRepository.findByEmail(email)
                    .filter(user -> !user.getId().equals(currentUserId))
                    .ifPresent(user -> {
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
                        throw new UserAlreadyExistsException("This phone number is already registered.");
                    });
        }
    }

    @Auditable(entityType = AuditEntityType.USER, action = AuditAction.STATUS_CHANGE)
    @Override
    @Transactional
    public UserResponse toggleUserEnabled(Long userId, CurrentActor actor) {
        User targetUser = fetchTargetUser(userId);

        accessPolicy.validateToggleEnabledAuthorization(actor, targetUser);

        // Toggle the enabled status
        boolean newEnabledStatus = !targetUser.getEnabled();
        targetUser.setEnabled(newEnabledStatus);

        User updatedUser = userRepository.save(targetUser);
        authUserCache.evict(updatedUser.getUsername());
        log.atInfo()
                .addKeyValue("event", "user.enabled.changed")
                .addKeyValue("targetUserId", userId)
                .addKeyValue("enabled", newEnabledStatus)
                .log("User enabled status changed");

        return responseMapper.toResponse(updatedUser);
    }

    @Auditable(entityType = AuditEntityType.USER, action = AuditAction.STATUS_CHANGE)
    @Override
    @Transactional
    public UserResponse toggleUserLocked(Long userId, CurrentActor actor) {
        User targetUser = fetchTargetUser(userId);

        accessPolicy.validateToggleLockedAuthorization(actor, targetUser);

        boolean newNonLockedStatus = !targetUser.getAccountNonLocked();
        targetUser.setAccountNonLocked(newNonLockedStatus);

        User updatedUser = userRepository.save(targetUser);
        authUserCache.evict(updatedUser.getUsername());
        log.atInfo()
                .addKeyValue("event", "user.locked.changed")
                .addKeyValue("targetUserId", userId)
                .addKeyValue("locked", !newNonLockedStatus)
                .log("User locked status changed");

        return responseMapper.toResponse(updatedUser);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserById(Long userId, CurrentActor actor) {
        User targetUser = fetchTargetUser(userId);

        // A target the caller may not view is reported as not found, so its existence
        // is not disclosed across organizations or privilege levels.
        if (!accessPolicy.canViewUser(actor, targetUser)) {
            logViewHidden("targetUserId", userId);
            throw new UserNotFoundException();
        }

        return responseMapper.toResponse(targetUser);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserByUsername(String username, CurrentActor actor) {
        User targetUser = fetchTargetUserByUsername(username);

        // A target the caller may not view is reported as not found, so that a username
        // belonging to a privileged or cross-organization account cannot be distinguished
        // from one that does not exist (no account-existence disclosure by username).
        if (!accessPolicy.canViewUser(actor, targetUser)) {
            logViewHidden("targetUsername", username);
            throw new UserNotFoundException();
        }

        return responseMapper.toResponse(targetUser);
    }

    /**
     * Fetch the target user by username
     */
    private User fetchTargetUserByUsername(String username) {
        return userRepository.findByUsername(username).orElseThrow(UserNotFoundException::new);
    }

    private void logViewHidden(String targetKey, Object target) {
        log.atWarn()
                .addKeyValue("event", "authz.denied")
                .addKeyValue("reason", "user_not_viewable")
                .addKeyValue(targetKey, target)
                .log("User read hidden as not found");
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserResponse> getUsers(List<UserRole> roles, Long organizationId, Long eventId, Boolean enabled,
                                       String search, Pageable pageable,
                                       CurrentActor actor) {
        UserRole currentRole = actor.role();

        Long scopedOrgId = organizationId;

        if (currentRole == UserRole.ORGANIZER_ADMIN || currentRole == UserRole.ORGANIZER_USER) {
            accessPolicy.requireOrganization(actor);
            scopedOrgId = actor.organizationId();
        }

        Specification<User> spec = buildUserSpecification(roles, scopedOrgId, eventId, enabled, search);
        return userRepository.findAll(spec, pageable).map(responseMapper::toResponse);
    }

    private Specification<User> buildUserSpecification(
            List<UserRole> roles, Long organizationId, Long eventId, Boolean enabled, String search) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Blank entries in the query string ("role=ROOT,") arrive as nulls; an IN list holding
            // one would silently match no user at all, so they are dropped before the predicate.
            List<UserRole> wantedRoles = roles == null ? List.of()
                    : roles.stream().filter(Objects::nonNull).toList();
            if (!wantedRoles.isEmpty()) {
                predicates.add(root.get("role").in(wantedRoles));
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
        return responseMapper.toResponse(fetchCurrentUser(actor.username()));
    }

    @Auditable(entityType = AuditEntityType.USER, action = AuditAction.DELETE)
    @Override
    @Transactional
    public void deleteUser(Long userId, CurrentActor actor) {
        User targetUser = fetchTargetUser(userId);

        accessPolicy.validateDeleteUserAuthorization(actor, targetUser);

        UserArchive archive = buildArchiveFromUser(targetUser, actor.username());
        userArchiveRepository.save(archive);

        int notificationsDeleted = notificationService.deleteAllForUser(targetUser.getId());

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

        log.atInfo()
                .addKeyValue("event", "user.archived")
                .addKeyValue("targetUserId", userId)
                .addKeyValue("targetUsername", username)
                .addKeyValue("targetRole", role)
                .addKeyValue("notificationsDeleted", notificationsDeleted)
                .log("User archived");
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
