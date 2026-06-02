package com.timekeeper.bibexpo.service.validator;

import com.timekeeper.bibexpo.exception.InvalidUserDataException;
import com.timekeeper.bibexpo.exception.UnauthorizedAccessException;
import com.timekeeper.bibexpo.model.entity.Event;
import com.timekeeper.bibexpo.model.entity.EventStatus;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.entity.UserRole;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static com.timekeeper.bibexpo.model.entity.EventStatus.CANCELLED;
import static com.timekeeper.bibexpo.model.entity.EventStatus.COMPLETED;
import static com.timekeeper.bibexpo.model.entity.EventStatus.DRAFT;
import static com.timekeeper.bibexpo.model.entity.EventStatus.PUBLISHED;

/**
 * Single authority for event status transitions.
 *
 * <p>Permitted moves live as data in {@link #ALLOWED}; extra conditions are
 * layered on top: an event may not return to draft once distribution has started
 * or its start date has passed, and only a platform administrator may reopen a
 * finished event (always to published, never to draft). A future rule means editing
 * the map or adding one condition, not growing an if-else chain. The check is pure
 * and only reads the event's own fields.
 */
@Component
public class EventStatusTransitionValidator {

    private static final Map<EventStatus, Set<EventStatus>> ALLOWED = Map.of(
            DRAFT, Set.of(PUBLISHED, CANCELLED),
            PUBLISHED, Set.of(DRAFT, COMPLETED, CANCELLED),
            COMPLETED, Set.of(PUBLISHED),
            CANCELLED, Set.of(PUBLISHED)
    );

    /**
     * Validates that the event may move to the target status for this user.
     *
     * @param event       the event being changed, read for its current status and distribution marker
     * @param target      the requested status
     * @param currentUser the user requesting the change
     * @throws InvalidUserDataException    if the move is not a permitted transition, or the event can
     *                                     no longer return to draft because distribution has started
     *                                     or its start date has passed
     * @throws UnauthorizedAccessException if a non-administrator tries to reopen a finished event
     */
    public void validateTransition(Event event, EventStatus target, User currentUser) {
        EventStatus current = event.getStatus();
        if (target == current) {
            return;
        }
        if (!ALLOWED.getOrDefault(current, Set.of()).contains(target)) {
            throw new InvalidUserDataException("You cannot change the event to this status.");
        }
        if (current == PUBLISHED && target == DRAFT) {
            if (Boolean.TRUE.equals(event.getDistributionStarted())) {
                throw new InvalidUserDataException(
                        "You cannot return this event to draft because distribution has already started.");
            }
            if (hasStarted(event)) {
                throw new InvalidUserDataException(
                        "You cannot return this event to draft because it has already started.");
            }
        }
        if (isReopen(current, target) && !isPlatformAdmin(currentUser)) {
            throw new UnauthorizedAccessException("You are not allowed to reopen a finished event.");
        }
    }

    private boolean isReopen(EventStatus current, EventStatus target) {
        return (current == COMPLETED || current == CANCELLED) && target == PUBLISHED;
    }

    private boolean hasStarted(Event event) {
        return event.getEventStartDate() != null && !Instant.now().isBefore(event.getEventStartDate());
    }

    private boolean isPlatformAdmin(User currentUser) {
        UserRole role = currentUser.getRole();
        return role == UserRole.ROOT || role == UserRole.ADMIN;
    }
}
