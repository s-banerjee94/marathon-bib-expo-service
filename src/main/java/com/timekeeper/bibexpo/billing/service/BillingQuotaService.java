package com.timekeeper.bibexpo.billing.service;

import com.timekeeper.bibexpo.model.entity.UserRole;

/**
 * Guards manual bill generation with a per-event, per-role quota and the one-final lock.
 *
 * <p>Each method runs in its own short transaction so a claim commits before the slow
 * Lambda call and never holds a database transaction open across it. The atomicity of a
 * claim comes from a single conditional update on the event's control row.
 */
public interface BillingQuotaService {

    /**
     * Ensures the event has a billing control row, creating it with empty counters if
     * absent. Idempotent and safe to call repeatedly; existing counters are preserved.
     *
     * @param eventId the event to initialise billing state for
     */
    void ensureState(Long eventId);

    /**
     * Attempts to claim one manual-request slot for the caller's role group, atomically.
     * ORGANIZER_ADMIN spends the organizer quota; ROOT/ADMIN spend the admin quota.
     *
     * @param eventId the event being billed
     * @param role    the requesting user's role
     * @return {@link QuotaClaimResult#CLAIMED} if a slot was taken, otherwise the reason it was refused
     */
    QuotaClaimResult claim(Long eventId, UserRole role);

    /**
     * Returns a previously claimed slot to the caller's role group. Used only when a
     * claimed request fails before any bill is produced.
     *
     * @param eventId the event to refund a slot for
     * @param role    the requesting user's role
     */
    void refund(Long eventId, UserRole role);

    /**
     * Whether a final bill already exists for the event. A finalize request is gated only by
     * this lock — never by the manual-request quota — so the one closing final stays reachable
     * even after the draft quota is spent.
     *
     * @param eventId the event to check
     * @return {@code true} if the event is finalized and cannot be billed again
     */
    boolean isFinalized(Long eventId);
}
