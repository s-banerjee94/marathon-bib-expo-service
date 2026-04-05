package com.timekeeper.bibexpo.service.validator;

import com.timekeeper.bibexpo.exception.EventDisabledException;
import com.timekeeper.bibexpo.exception.UnauthorizedAccessException;
import com.timekeeper.bibexpo.model.entity.Event;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.entity.UserRole;
import org.springframework.stereotype.Component;

@Component
public class EventAccessValidator {

    /**
     * Validates that the user belongs to the same organization as the event AND the event is enabled.
     * Use for participant write operations where event status matters.
     */
    public void validateUserAuthorizationForEvent(User currentUser, Event event) {
        if (Boolean.FALSE.equals(event.getEnabled())) {
            throw new EventDisabledException(
                    "Event is disabled. Operations are not allowed for event ID: " + event.getId());
        }

        UserRole role = currentUser.getRole();

        if (role == UserRole.ROOT || role == UserRole.ADMIN) {
            return;
        }

        if (role == UserRole.ORGANIZER_ADMIN || role == UserRole.ORGANIZER_USER || role == UserRole.DISTRIBUTOR) {
            if (currentUser.getOrganization() == null) {
                throw new UnauthorizedAccessException("User does not belong to any organization");
            }

            if (!event.getOrganization().getId().equals(currentUser.getOrganization().getId())) {
                throw new UnauthorizedAccessException(
                        "User can only access events from their own organization");
            }
            return;
        }

        throw new UnauthorizedAccessException("User does not have permission to access events");
    }

    /**
     * Validates only org membership, without checking event-enabled status.
     * Use for read-only operations (import history, error logs) that should work even on disabled events.
     */
    public void validateUserOrganizationAccess(User currentUser, Event event) {
        UserRole role = currentUser.getRole();

        if (role == UserRole.ROOT || role == UserRole.ADMIN) return;

        if (role == UserRole.ORGANIZER_ADMIN || role == UserRole.ORGANIZER_USER || role == UserRole.DISTRIBUTOR) {
            if (currentUser.getOrganization() == null) {
                throw new UnauthorizedAccessException("User does not belong to any organization");
            }
            if (!event.getOrganization().getId().equals(currentUser.getOrganization().getId())) {
                throw new UnauthorizedAccessException("User can only access events from their own organization");
            }
            return;
        }

        throw new UnauthorizedAccessException("User does not have permission to access events");
    }
}
