package com.timekeeper.bibexpo.service.validator;

import com.timekeeper.bibexpo.exception.EventNotFoundException;
import com.timekeeper.bibexpo.exception.ParticipantDeletionNotAllowedException;
import com.timekeeper.bibexpo.exception.ParticipantModificationNotAllowedException;
import com.timekeeper.bibexpo.model.entity.Event;
import com.timekeeper.bibexpo.model.entity.EventStatus;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Resolves an event and enforces the participant-management preconditions for one intent.
 *
 * <p>Each entry method loads the event, applies organization/availability gating through
 * {@link EventAccessValidator}, and layers the status rule for that intent. {@link #forDelete}
 * returns the resolved event for callers that reuse it; the others are pure guards. Callers
 * replace the repeated find-then-check preamble with a single line.
 */
@Component
@RequiredArgsConstructor
public class ParticipantAccessGuard {

    private final EventRepository eventRepository;
    private final EventAccessValidator eventAccessValidator;

    /**
     * Read or list access: the caller's organization must own the event and the event must be available.
     *
     * @param eventId     the event being accessed
     * @param currentUser the authenticated user
     */
    public void forRead(Long eventId, User currentUser) {
        Event event = findEvent(eventId);
        eventAccessValidator.validateUserAuthorizationForEvent(currentUser, event);
    }

    /**
     * Create or edit a participant: allowed while the event is DRAFT or PUBLISHED, blocked once it is
     * COMPLETED or CANCELLED.
     *
     * @param eventId     the event being modified
     * @param currentUser the authenticated user
     */
    public void forWrite(Long eventId, User currentUser) {
        Event event = findEvent(eventId);
        eventAccessValidator.validateUserAuthorizationForEvent(currentUser, event);
        requireNotFinal(event);
    }

    /**
     * Delete a participant: allowed only while the event is DRAFT.
     *
     * @param eventId     the event being modified
     * @param currentUser the authenticated user
     * @return the resolved event
     */
    public Event forDelete(Long eventId, User currentUser) {
        Event event = findEvent(eventId);
        eventAccessValidator.validateUserAuthorizationForEvent(currentUser, event);
        requireDraft(event);
        return event;
    }

    private Event findEvent(Long eventId) {
        return eventRepository.findById(eventId).orElseThrow(EventNotFoundException::new);
    }

    private void requireDraft(Event event) {
        if (event.getStatus() != EventStatus.DRAFT) {
            throw new ParticipantDeletionNotAllowedException(
                    "You can only delete participants while the event is in draft.");
        }
    }

    private void requireNotFinal(Event event) {
        EventStatus status = event.getStatus();
        if (status == EventStatus.COMPLETED || status == EventStatus.CANCELLED) {
            throw new ParticipantModificationNotAllowedException(
                    "You cannot add or edit participants once the event is completed or cancelled.");
        }
    }
}
