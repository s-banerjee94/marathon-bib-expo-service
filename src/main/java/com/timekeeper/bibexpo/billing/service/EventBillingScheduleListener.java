package com.timekeeper.bibexpo.billing.service;

import com.timekeeper.bibexpo.model.entity.EventStatus;
import com.timekeeper.bibexpo.model.event.EventStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Drives the auto-billing timer off committed event status changes: a terminal
 * status arms the bill; reopening (back to a working status) cancels the pending
 * timer. Runs after commit so a scheduling hiccup never rolls back the status
 * change itself.
 */
@Component
@RequiredArgsConstructor
public class EventBillingScheduleListener {

    private final BillingScheduleService billingScheduleService;
    private final BillingQuotaService billingQuotaService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStatusChanged(EventStatusChangedEvent event) {
        if (isTerminal(event.newStatus())) {
            // Quota persists across reopen → re-complete: the row is created once and never reset.
            billingQuotaService.ensureState(event.eventId());
            billingScheduleService.schedule(event.eventId());
        } else {
            billingScheduleService.cancel(event.eventId());
        }
    }

    private boolean isTerminal(EventStatus status) {
        return status == EventStatus.COMPLETED || status == EventStatus.CANCELLED;
    }
}
