package com.timekeeper.bibexpo.service;

/**
 * Port that lets core event flows consult the billing slice without depending on it
 * directly — the billing slice provides the implementation. Keeps the dependency pointing
 * from billing to core, matching the rest of the slice's decoupling.
 */
public interface EventBillingGuard {

    /**
     * Whether the event has an issued (FINAL) bill. Once it does, the event is closed
     * permanently and must not be reopened.
     *
     * @param eventId the event to check
     * @return true if a final invoice exists for the event
     */
    boolean hasFinalInvoice(Long eventId);
}
