package com.timekeeper.bibexpo.billing.service;

/**
 * Manages the deferred auto-billing timer for an event. The bill itself is produced
 * by an external Lambda; this service only arms or cancels the one-time schedule that
 * invokes it after a configured delay.
 */
public interface BillingScheduleService {

    /**
     * Arm (or re-arm) auto-billing for the event, firing after the configured delay.
     * Safe to call repeatedly — the most recent call wins.
     *
     * @param eventId the event to bill
     */
    void schedule(Long eventId);

    /**
     * Cancel any pending auto-billing timer for the event. No-op if none exists.
     *
     * @param eventId the event whose pending bill should be cancelled
     */
    void cancel(Long eventId);
}
