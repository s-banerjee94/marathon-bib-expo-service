package com.timekeeper.bibexpo.service.validator;

import com.timekeeper.bibexpo.exception.EventDisabledException;
import com.timekeeper.bibexpo.exception.EventOperationNotAllowedException;
import com.timekeeper.bibexpo.exception.UnauthorizedAccessException;
import com.timekeeper.bibexpo.model.entity.Event;
import com.timekeeper.bibexpo.model.entity.EventStatus;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.entity.UserRole;
import com.timekeeper.bibexpo.service.EventBillingGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DistributionValidator {

    private final EventAccessValidator eventAccessValidator;
    private final EventBillingGuard billingGuard;

    /**
     * Distribution actions (bib collection, undo, and goodies hand-out) may proceed
     * only while the event is published. This status gate applies to every role,
     * platform admins included.
     *
     * @throws EventDisabledException if the event is not in the published state
     */
    public void validateDistributionAllowed(Event event) {
        if (billingGuard.hasFinalInvoice(event.getId())) {
            throw new EventOperationNotAllowedException(
                    "You cannot make changes once the event bill has been finalized.");
        }
        if (event.getStatus() != EventStatus.PUBLISHED) {
            throw new EventDisabledException("Bib distribution is only allowed while the event is published.");
        }
    }

    public void validateUserAuthorizationForEvent(User currentUser, Event event) {
        eventAccessValidator.validateEventAvailability(currentUser, event);

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
            eventAccessValidator.enforceDistributorEventScope(currentUser, event);
            return;
        }

        throw new UnauthorizedAccessException("User does not have permission to access events");
    }

    public void validateUserAuthorizationForUndoOperation(User currentUser, Event event) {
        eventAccessValidator.validateEventAvailability(currentUser, event);

        UserRole role = currentUser.getRole();

        if (role == UserRole.ROOT || role == UserRole.ADMIN) {
            return;
        }

        if (orgUserValidation(currentUser, event, role)) return;

        throw new UnauthorizedAccessException(
                "User does not have permission to undo distribution operations.");
    }

    public boolean orgUserValidation(User currentUser, Event event, UserRole role) {
        if (role == UserRole.ORGANIZER_ADMIN || role == UserRole.ORGANIZER_USER) {
            if (currentUser.getOrganization() == null) {
                throw new UnauthorizedAccessException("User does not belong to any organization");
            }

            if (!event.getOrganization().getId().equals(currentUser.getOrganization().getId())) {
                throw new UnauthorizedAccessException(
                        "User can only access events from their own organization");
            }
            return true;
        }
        return false;
    }

    public void validateUserAuthorizationForLogAccess(User currentUser, Event event) {
        eventAccessValidator.validateEventAvailability(currentUser, event);

        UserRole role = currentUser.getRole();

        if (role == UserRole.ROOT || role == UserRole.ADMIN) {
            return;
        }

        if (role == UserRole.ORGANIZER_ADMIN) {
            if (currentUser.getOrganization() == null) {
                throw new UnauthorizedAccessException("User does not belong to any organization");
            }

            if (!event.getOrganization().getId().equals(currentUser.getOrganization().getId())) {
                throw new UnauthorizedAccessException(
                        "User can only access events from their own organization");
            }
            return;
        }

        throw new UnauthorizedAccessException(
                "User does not have permission to access distribution logs.");
    }
}
