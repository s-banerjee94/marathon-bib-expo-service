package com.timekeeper.bibexpo.billing.service;

/**
 * Outcome of trying to claim a manual bill-request slot for an event.
 */
public enum QuotaClaimResult {

    /** A slot was claimed; the request may proceed. */
    CLAIMED,

    /** The caller's role group has spent its per-event quota. */
    QUOTA_EXHAUSTED,

    /** A final bill already exists, so the event is closed to all further requests. */
    FINALIZED
}
