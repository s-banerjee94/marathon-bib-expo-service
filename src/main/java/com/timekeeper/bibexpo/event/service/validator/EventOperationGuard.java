package com.timekeeper.bibexpo.event.service.validator;

import com.timekeeper.bibexpo.event.exception.EventOperationNotAllowedException;
import com.timekeeper.bibexpo.event.model.entity.Event;
import com.timekeeper.bibexpo.event.model.entity.EventStatus;
import com.timekeeper.bibexpo.event.model.enums.EventOperation;
import com.timekeeper.bibexpo.event.api.EventBillingGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Single authority for event-state and billing write gates.
 * Call {@link #requireAllowed(Event, EventOperation)} before any write on event-scoped resources.
 * Does not enforce role or organization ownership — those stay in {@link EventAccessValidator}.
 */
@Component
@RequiredArgsConstructor
public class EventOperationGuard {

    private final EventBillingGuard billingGuard;

    /**
     * Throws {@link EventOperationNotAllowedException} if the requested operation is not
     * permitted given the event's current status or billing state.
     */
    public void requireAllowed(Event event, EventOperation operation) {
        if (billingGuard.hasFinalInvoice(event.getId())) {
            throw new EventOperationNotAllowedException(
                    "You cannot make changes once the event bill has been finalized.");
        }

        EventStatus status = event.getStatus();

        if (status == EventStatus.CANCELLED) {
            throw new EventOperationNotAllowedException(
                    "You cannot make changes to a cancelled event.");
        }

        if (status == EventStatus.COMPLETED) {
            if (operation != EventOperation.TEMPLATE_WRITE && operation != EventOperation.CAMPAIGN_WRITE) {
                throw new EventOperationNotAllowedException(
                        "You cannot make changes once the event is completed.");
            }
            return;
        }

        // Status is DRAFT or PUBLISHED from here.
        if (operation == EventOperation.DISTRIBUTION && status != EventStatus.PUBLISHED) {
            throw new EventOperationNotAllowedException(
                    "Distribution is only allowed while the event is published.");
        }

        if (status == EventStatus.PUBLISHED) {
            switch (operation) {
                case RACE_WRITE -> throw new EventOperationNotAllowedException(
                        "Races cannot be modified after the event has been published.");
                case CATEGORY_WRITE -> throw new EventOperationNotAllowedException(
                        "Categories cannot be modified after the event has been published.");
                case FULL_IMPORT -> throw new EventOperationNotAllowedException(
                        "A full import is only allowed while the event is in draft.");
                case GOODIE_PURGE -> throw new EventOperationNotAllowedException(
                        "You can only remove an imported goody while the event is in draft.");
                default -> { /* allowed */ }
            }
        }
    }
}
