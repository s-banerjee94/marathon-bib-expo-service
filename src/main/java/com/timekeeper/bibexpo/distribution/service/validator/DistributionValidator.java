package com.timekeeper.bibexpo.distribution.service.validator;

import com.timekeeper.bibexpo.event.exception.EventDisabledException;
import com.timekeeper.bibexpo.event.exception.EventOperationNotAllowedException;
import com.timekeeper.bibexpo.event.model.entity.Event;
import com.timekeeper.bibexpo.event.model.entity.EventStatus;
import com.timekeeper.bibexpo.event.api.EventBillingGuard;
import com.timekeeper.bibexpo.event.service.validator.EventAccessValidator;
import com.timekeeper.bibexpo.shared.error.AccessForbiddenException;
import com.timekeeper.bibexpo.shared.security.UserRole;
import com.timekeeper.bibexpo.user.model.entity.User;
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

    /**
     * Full access to act on the event, delegated to {@link EventAccessValidator} so that a
     * request for another organization's event is reported as not found here exactly as it is
     * everywhere else. Answering 403 instead told the caller the event existed.
     *
     * @throws AccessForbiddenException if the caller has no organization
     */
    public void validateUserAuthorizationForEvent(User currentUser, Event event) {
        eventAccessValidator.validateUserAuthorizationForEvent(currentUser, event);
    }

    /**
     * Undo is barred to distributors: correcting a hand-out is the organizer's call.
     *
     * @throws AccessForbiddenException if the caller is a distributor
     */
    public void validateUserAuthorizationForUndoOperation(User currentUser, Event event) {
        eventAccessValidator.validateUserAuthorizationForEvent(currentUser, event);
        denyDistributor(currentUser, "You do not have permission to undo a bib collection.");
    }

    /**
     * Distribution logs cover the whole event, so they are barred to distributors.
     *
     * @throws AccessForbiddenException if the caller is a distributor
     */
    public void validateUserAuthorizationForLogAccess(User currentUser, Event event) {
        eventAccessValidator.validateUserAuthorizationForEvent(currentUser, event);
        denyDistributor(currentUser, "You do not have permission to view distribution logs.");
    }

    // Backs up the @PreAuthorize on the controller interface rather than restating it: the
    // annotation is the gate, this survives it being relaxed.
    private void denyDistributor(User currentUser, String message) {
        if (currentUser.getRole() == UserRole.DISTRIBUTOR) {
            throw new AccessForbiddenException(message);
        }
    }
}
