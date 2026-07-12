package com.timekeeper.bibexpo.service.validator;

import com.timekeeper.bibexpo.exception.AccessForbiddenException;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.entity.UserRole;
import com.timekeeper.bibexpo.security.CurrentActor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Central authority for user-management access decisions.
 *
 * <p>Coarse role gating (which roles may reach an endpoint) is handled by
 * {@code @PreAuthorize} on the controllers. This component owns the fine-grained,
 * data-dependent rules: the role-creation hierarchy, self-action guards, and
 * organization scoping between the acting user and the target user.
 */
@Component
@Slf4j
public class UserAccessPolicy {

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

    /**
     * Validate that ROOT cannot be created via API.
     *
     * @throws AccessForbiddenException if the requested role is ROOT
     */
    public void validateRootCreationAttempt(UserRole requestedRole, String currentUsername) {
        if (requestedRole == UserRole.ROOT) {
            log.error("Attempt to create ROOT user by: {}", currentUsername);
            throw new AccessForbiddenException("ROOT users cannot be created.");
        }
    }

    /**
     * Validate that the actor may create a user with the requested role in the target
     * organization, applying the role-creation hierarchy and organization scoping.
     *
     * @throws AccessForbiddenException if the role is not creatable by the actor,
     *         or the target organization is not the actor's own
     */
    public void validateCreateUserAuthorization(CurrentActor actor, UserRole requestedRole, Long targetOrganizationId) {
        UserRole currentRole = actor.role();
        Set<UserRole> allowed = CREATABLE_ROLES.getOrDefault(currentRole, EnumSet.noneOf(UserRole.class));

        if (!allowed.contains(requestedRole)) {
            log.error("User {} with role {} attempted to create {} user",
                    actor.username(), currentRole, requestedRole);
            throw new AccessForbiddenException("You are not allowed to create users with this role.");
        }

        if (currentRole == UserRole.ORGANIZER_ADMIN || currentRole == UserRole.ORGANIZER_USER) {
            if (actor.organizationId() == null) {
                log.error("{} {} has no organization assigned", currentRole, actor.username());
                throw new AccessForbiddenException("Your account is not assigned to an organization.");
            }
            if (!actor.organizationId().equals(targetOrganizationId)) {
                log.error("{} {} attempted to create user for org {}. Own org: {}",
                        currentRole, actor.username(),
                        targetOrganizationId, actor.organizationId());
                throw new AccessForbiddenException("You can only create users in your organization.");
            }
        }

        log.debug("Authorization validated for {} to create role {}", actor.username(), requestedRole);
    }

    /**
     * Authorize a distributor reassignment. ROOT and ADMIN may reassign any distributor;
     * ORGANIZER_ADMIN and ORGANIZER_USER only distributors in their own organization.
     *
     * @throws AccessForbiddenException if the actor may not reassign the target
     */
    public void validateReassignAuthorization(CurrentActor actor, User targetUser) {
        UserRole currentRole = actor.role();
        if (currentRole == UserRole.ROOT || currentRole == UserRole.ADMIN) {
            return;
        }
        if (currentRole == UserRole.ORGANIZER_ADMIN || currentRole == UserRole.ORGANIZER_USER) {
            validateSameOrganization(actor, targetUser, "reassign");
            return;
        }
        throw new AccessForbiddenException("You are not allowed to reassign distributors.");
    }

    /**
     * Validates that the actor has permission to update the target user.
     *
     * Permission hierarchy:
     * - ROOT can update: any user
     * - ADMIN can update: itself, ORG_ADMIN, ORG_USER, DISTRIBUTOR (but not ROOT or other ADMINs)
     * - ORG_ADMIN can update: itself, ORG_USER, DISTRIBUTOR (own organization only)
     *
     * @throws AccessForbiddenException if the actor may not update the target
     */
    public void validateUpdateUserAuthorization(CurrentActor actor, User targetUser) {
        UserRole currentRole = actor.role();

        // ROOT can update anyone
        if (currentRole == UserRole.ROOT) {
            log.debug("ROOT user authorized to update user ID: {}", targetUser.getId());
            return;
        }

        // Delegate to role-specific validators
        if (currentRole == UserRole.ADMIN) {
            validateAdminUpdateAuthorization(actor, targetUser);
            return;
        }

        if (currentRole == UserRole.ORGANIZER_ADMIN) {
            validateOrgAdminUpdateAuthorization(actor, targetUser);
            return;
        }

        if (currentRole == UserRole.ORGANIZER_USER) {
            validateOrgUserUpdateAuthorization(actor, targetUser);
            return;
        }

        if (currentRole == UserRole.DISTRIBUTOR) {
            validateSelfUpdateOnly(actor, targetUser);
            return;
        }

        // Other roles cannot update users
        log.error("User {} with role {} attempted to update user",
                actor.username(), currentRole);
        throw new AccessForbiddenException("You are not allowed to update users.");
    }

    /**
     * Validates that the actor has permission to toggle the enabled status of the target user.
     *
     * Permission hierarchy:
     * - ROOT can disable: any other user
     * - ADMIN can disable: ORG_ADMIN, ORG_USER, DISTRIBUTOR (not ROOT or other ADMINs)
     * - ORG_ADMIN can disable: ORG_USER, DISTRIBUTOR (own organization only)
     * - ORG_USER can disable: DISTRIBUTOR (own organization only)
     * - No one can enable or disable their own account
     *
     * @throws AccessForbiddenException if the actor may not toggle the target
     */
    public void validateToggleEnabledAuthorization(CurrentActor actor, User targetUser) {
        UserRole currentRole = actor.role();
        UserRole targetRole = targetUser.getRole();

        // No one may enable or disable their own account (prevents self-lockout).
        if (isSelfUpdate(actor, targetUser)) {
            log.error("User {} attempted to toggle its own enabled status", actor.username());
            throw new AccessForbiddenException("You cannot enable or disable your own account.");
        }

        // ROOT can disable any other user
        if (currentRole == UserRole.ROOT) {
            log.debug("ROOT user authorized to toggle enabled status for user ID: {}", targetUser.getId());
            return;
        }

        // ADMIN can disable organization roles, but not ROOT or another ADMIN
        if (currentRole == UserRole.ADMIN) {
            if (targetRole == UserRole.ROOT || targetRole == UserRole.ADMIN) {
                log.error("ADMIN {} attempted to toggle enabled status for {} user",
                        actor.username(), targetRole);
                throw new AccessForbiddenException(
                        "You cannot enable or disable users with equal or higher privileges.");
            }
            log.debug("ADMIN user authorized to toggle enabled status for user ID: {}", targetUser.getId());
            return;
        }

        // ORG_ADMIN restrictions
        if (currentRole == UserRole.ORGANIZER_ADMIN) {
            // ORG_ADMIN cannot disable ROOT, ADMIN, or other ORG_ADMINs
            if (isPrivilegedRole(targetRole)) {
                log.error("ORG_ADMIN {} attempted to toggle enabled status for {} user",
                        actor.username(), targetRole);
                throw new AccessForbiddenException(
                        "You cannot enable or disable users with equal or higher privileges.");
            }

            // ORG_ADMIN can only disable users within their own organization
            validateSameOrganization(actor, targetUser, "disable");

            log.debug("ORG_ADMIN user authorized to toggle enabled status for user ID: {}", targetUser.getId());
            return;
        }

        // ORG_USER restrictions
        if (currentRole == UserRole.ORGANIZER_USER) {
            if (targetRole != UserRole.DISTRIBUTOR) {
                log.error("ORG_USER {} attempted to toggle enabled status for {} user",
                        actor.username(), targetRole);
                throw new AccessForbiddenException(
                        "You can only enable or disable distributor accounts.");
            }

            validateSameOrganization(actor, targetUser, "disable");

            log.debug("ORG_USER user authorized to toggle enabled status for distributor ID: {}", targetUser.getId());
            return;
        }

        // Other roles (DISTRIBUTOR) cannot toggle enabled status
        log.error("User {} with role {} attempted to toggle enabled status",
                actor.username(), currentRole);
        throw new AccessForbiddenException("You are not allowed to enable or disable users.");
    }

    /**
     * Validates that the actor has permission to toggle the locked status of the target user.
     *
     * Permission hierarchy:
     * - ROOT can lock/unlock: any other user
     * - ADMIN can lock/unlock: ORG_ADMIN, ORG_USER, DISTRIBUTOR (not ROOT or other ADMINs)
     * - No one can lock or unlock their own account
     *
     * @throws AccessForbiddenException if the actor may not toggle the target
     */
    public void validateToggleLockedAuthorization(CurrentActor actor, User targetUser) {
        UserRole currentRole = actor.role();
        UserRole targetRole = targetUser.getRole();

        // No one may lock or unlock their own account.
        if (isSelfUpdate(actor, targetUser)) {
            log.error("User {} attempted to toggle its own locked status", actor.username());
            throw new AccessForbiddenException("You cannot lock or unlock your own account.");
        }

        // ROOT can lock/unlock any other user
        if (currentRole == UserRole.ROOT) {
            log.debug("ROOT user authorized to toggle locked status for user ID: {}", targetUser.getId());
            return;
        }

        // ADMIN can lock/unlock organization roles, but not ROOT or another ADMIN
        if (currentRole == UserRole.ADMIN) {
            if (targetRole == UserRole.ROOT || targetRole == UserRole.ADMIN) {
                log.error("ADMIN {} attempted to toggle locked status for {} user",
                        actor.username(), targetRole);
                throw new AccessForbiddenException(
                        "You cannot lock or unlock users with equal or higher privileges.");
            }
            log.debug("ADMIN user authorized to toggle locked status for user ID: {}", targetUser.getId());
            return;
        }

        log.error("User {} with role {} attempted to toggle locked status",
                actor.username(), currentRole);
        throw new AccessForbiddenException("You are not allowed to lock or unlock users.");
    }

    /**
     * Validates that the actor has permission to archive the target user.
     * ROOT users can never be archived.
     *
     * @throws AccessForbiddenException if the actor may not archive the target
     */
    public void validateDeleteUserAuthorization(CurrentActor actor, User targetUser) {
        if (targetUser.getRole() == UserRole.ROOT) {
            log.error("Attempt to archive ROOT user by: {}", actor.username());
            throw new AccessForbiddenException("ROOT users cannot be archived.");
        }

        if (isSelfUpdate(actor, targetUser)) {
            log.error("User {} attempted to archive itself", actor.username());
            throw new AccessForbiddenException("You cannot archive your own account.");
        }

        UserRole currentRole = actor.role();

        if (currentRole == UserRole.ROOT) {
            return;
        }

        if (currentRole == UserRole.ADMIN) {
            if (targetUser.getRole() == UserRole.ADMIN) {
                throw new AccessForbiddenException("You cannot archive users with equal or higher privileges.");
            }
            return;
        }

        if (currentRole == UserRole.ORGANIZER_ADMIN) {
            if (isPrivilegedRole(targetUser.getRole())) {
                throw new AccessForbiddenException("You cannot archive users with equal or higher privileges.");
            }
            validateSameOrganization(actor, targetUser, "archive");
            return;
        }

        if (currentRole == UserRole.ORGANIZER_USER) {
            if (targetUser.getRole() != UserRole.DISTRIBUTOR) {
                log.error("ORG_USER {} attempted to archive {} user",
                        actor.username(), targetUser.getRole());
                throw new AccessForbiddenException("You can only archive distributor accounts.");
            }
            validateSameOrganization(actor, targetUser, "archive");
            return;
        }

        log.error("User {} with role {} attempted to archive a user",
                actor.username(), currentRole);
        throw new AccessForbiddenException("You are not allowed to archive users.");
    }

    /**
     * Whether {@code actor} is permitted to view {@code targetUser}: ROOT and ADMIN may
     * view anyone, organization-scoped roles only users within their own organization.
     */
    public boolean canViewUser(CurrentActor actor, User targetUser) {
        UserRole currentRole = actor.role();
        if (currentRole == UserRole.ROOT || currentRole == UserRole.ADMIN) {
            return true;
        }
        return actor.organizationId() != null
                && targetUser.getOrganization() != null
                && actor.organizationId().equals(targetUser.getOrganization().getId());
    }

    /**
     * Require the actor to belong to an organization.
     *
     * @throws AccessForbiddenException if the actor has no organization
     */
    public void requireOrganization(CurrentActor actor) {
        if (actor.organizationId() == null) {
            log.error("{} {} has no organization assigned", actor.role(), actor.username());
            throw new AccessForbiddenException("Your account is not assigned to an organization.");
        }
    }

    /**
     * Validates that the actor and target user belong to the same organization.
     * Used to enforce organization-scoped permissions.
     *
     * @param actor The user performing the action
     * @param targetUser The user being acted upon
     * @param action Description of the action being performed (for error messages)
     * @throws AccessForbiddenException if users are not in the same organization or organization is null
     */
    private void validateSameOrganization(CurrentActor actor, User targetUser, String action) {
        requireOrganization(actor);

        if (targetUser.getOrganization() == null ||
            !actor.organizationId().equals(targetUser.getOrganization().getId())) {
            log.error("{} {} attempted to {} user from different organization",
                    actor.role(), actor.username(), action);
            throw new AccessForbiddenException("You can only " + action + " users within your organization.");
        }
    }

    /**
     * Validates ADMIN user update permissions
     */
    private void validateAdminUpdateAuthorization(CurrentActor actor, User targetUser) {
        if (isSelfUpdate(actor, targetUser)) {
            log.debug("ADMIN user updating itself");
            return;
        }

        UserRole targetRole = targetUser.getRole();

        // ADMIN cannot update ROOT
        if (targetRole == UserRole.ROOT) {
            log.error("ADMIN {} attempted to update ROOT user", actor.username());
            throw new AccessForbiddenException("You cannot update users with equal or higher privileges.");
        }

        // ADMIN cannot update other ADMINs
        if (targetRole == UserRole.ADMIN) {
            log.error("ADMIN {} attempted to update another ADMIN user", actor.username());
            throw new AccessForbiddenException("You cannot update users with equal or higher privileges.");
        }

        // ADMIN can update ORG_ADMIN, ORG_USER, DISTRIBUTOR
        log.debug("ADMIN user authorized to update user ID: {}", targetUser.getId());
    }

    /**
     * Validates ORG_ADMIN user update permissions
     */
    private void validateOrgAdminUpdateAuthorization(CurrentActor actor, User targetUser) {
        if (isSelfUpdate(actor, targetUser)) {
            log.debug("ORG_ADMIN user updating itself");
            return;
        }

        UserRole targetRole = targetUser.getRole();

        // ORG_ADMIN cannot update ROOT, ADMIN, or other ORG_ADMINs
        if (isPrivilegedRole(targetRole)) {
            log.error("ORG_ADMIN {} attempted to update {} user",
                    actor.username(), targetRole);
            throw new AccessForbiddenException("You cannot update users with equal or higher privileges.");
        }

        // ORG_ADMIN can only update users within their own organization
        validateSameOrganization(actor, targetUser, "update");

        log.debug("ORG_ADMIN user authorized to update user ID: {}", targetUser.getId());
    }

    private void validateOrgUserUpdateAuthorization(CurrentActor actor, User targetUser) {
        if (isSelfUpdate(actor, targetUser)) {
            log.debug("ORG_USER user updating itself");
            return;
        }

        if (targetUser.getRole() != UserRole.DISTRIBUTOR) {
            log.error("ORG_USER {} attempted to update {} user",
                    actor.username(), targetUser.getRole());
            throw new AccessForbiddenException("You can only update distributor accounts.");
        }

        validateSameOrganization(actor, targetUser, "update");
        log.debug("ORG_USER user authorized to update distributor ID: {}", targetUser.getId());
    }

    /**
     * Validates that users can only update themselves
     */
    private void validateSelfUpdateOnly(CurrentActor actor, User targetUser) {
        if (isSelfUpdate(actor, targetUser)) {
            log.debug("{} user updating itself", actor.role());
            return;
        }

        log.error("User {} with role {} attempted to update another user",
                actor.username(), actor.role());
        throw new AccessForbiddenException("You can only update your own profile");
    }

    private boolean isSelfUpdate(CurrentActor actor, User targetUser) {
        return actor.id().equals(targetUser.getId());
    }

    private boolean isPrivilegedRole(UserRole role) {
        return PRIVILEGED_ROLES.contains(role);
    }
}
