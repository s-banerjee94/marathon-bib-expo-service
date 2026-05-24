package com.timekeeper.bibexpo.service.validator;

import com.timekeeper.bibexpo.exception.EventDisabledException;
import com.timekeeper.bibexpo.exception.EventNotFoundException;
import com.timekeeper.bibexpo.exception.UnauthorizedAccessException;
import com.timekeeper.bibexpo.model.entity.Event;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.entity.UserRole;
import org.springframework.stereotype.Component;

/**
 * Central authority for event-scoped access decisions.
 *
 * <p>Coarse role gating (which roles may reach an endpoint) is handled by
 * {@code @PreAuthorize} on the controllers. This component owns the fine-grained,
 * data-dependent rules: organization ownership and event availability.
 * ROOT and ADMIN bypass these checks.
 *
 * <p>{@link #enforceEventAvailable(Event)} is the single extension point for
 * event-level restrictions. New constraints (for example subscription-plan or
 * feature-gate rules) added there apply to every event-scoped operation
 * automatically.
 */
@Component
public class EventAccessValidator {

    /**
     * Full access required to operate on an event or any of its child resources.
     * The caller's organization must own the event and the event must be available.
     *
     * @throws UnauthorizedAccessException if the user does not own the event
     * @throws EventDisabledException      if the event is not available
     */
    public void validateUserAuthorizationForEvent(User currentUser, Event event) {
        if (isPlatformAdmin(currentUser)) {
            return;
        }
        requireSameOrganization(currentUser, event);
        enforceEventAvailable(event);
    }

    /**
     * Organization-scoped read access that stays available even when the event is
     * not operable (for example import history or error logs).
     *
     * @throws UnauthorizedAccessException if the user does not own the event
     */
    public void validateUserOrganizationAccess(User currentUser, Event event) {
        if (isPlatformAdmin(currentUser)) {
            return;
        }
        requireSameOrganization(currentUser, event);
    }

    /**
     * Availability check that keeps the ROOT/ADMIN bypass but skips the organization
     * check, for callers that have already established organization ownership.
     *
     * @throws EventDisabledException if the event is not available
     */
    public void validateEventAvailability(User currentUser, Event event) {
        if (isPlatformAdmin(currentUser)) {
            return;
        }
        enforceEventAvailable(event);
    }

    /**
     * Single source of truth for event-level constraints. Future subscription-plan
     * or feature-gate restrictions belong here so that every caller enforces them.
     *
     * @throws EventDisabledException if the event is not available
     */
    public void enforceEventAvailable(Event event) {
        if (Boolean.FALSE.equals(event.getEnabled())) {
            throw new EventDisabledException("This event is currently disabled.");
        }
    }

    private boolean isPlatformAdmin(User currentUser) {
        UserRole role = currentUser.getRole();
        return role == UserRole.ROOT || role == UserRole.ADMIN;
    }

    private void requireSameOrganization(User currentUser, Event event) {
        if (currentUser.getOrganization() == null) {
            throw new UnauthorizedAccessException("Your account is not assigned to an organization.");
        }
        // Events outside the caller's organization are reported as not found so their
        // existence is not disclosed across organizations.
        if (!event.getOrganization().getId().equals(currentUser.getOrganization().getId())) {
            throw new EventNotFoundException();
        }
    }
}
