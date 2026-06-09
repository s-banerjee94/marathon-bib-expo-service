package com.timekeeper.bibexpo.billing.service;

/**
 * Fires the dedicated bill-stats Lambda to recompute the platform billing snapshot. All stat
 * computation lives in the Lambda; Spring never recomputes. The invoke is asynchronous and
 * best-effort — a failure is only logged, leaving the previous snapshot in place until the next
 * trigger or a manual refresh. Triggered after a bill is finalized, after its payment state is
 * toggled, and on an explicit admin refresh.
 */
public interface BillStatsTriggerService {

    /**
     * Asynchronously invoke the bill-stats Lambda (fire-and-forget). Never throws — a failed invoke
     * only logs.
     *
     * @param reason what prompted the recompute (FINALIZE, PAYMENT or MANUAL); passed to the Lambda
     *               and recorded on the snapshot
     */
    void recomputeAsync(String reason);
}
