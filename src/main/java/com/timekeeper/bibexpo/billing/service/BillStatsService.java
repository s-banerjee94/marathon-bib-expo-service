package com.timekeeper.bibexpo.billing.service;

import com.timekeeper.bibexpo.billing.model.dto.response.BillStatsRefreshResponse;
import com.timekeeper.bibexpo.billing.model.dto.response.BillStatsResponse;
import com.timekeeper.bibexpo.model.enums.DashboardRange;

/**
 * Read side of platform billing statistics. The figures are computed entirely by the dedicated
 * bill-stats Lambda and stored as a snapshot; this service only reads that snapshot and slices it to
 * the requested range. It never computes a statistic. A manual refresh re-triggers the Lambda.
 */
public interface BillStatsService {

    /**
     * Return the billing statistics for one range window from the latest snapshot.
     *
     * @param range the range window (ALL, YEAR or MONTH)
     * @return the range slice; an all-zero slice with {@code refreshedAt = null} if the snapshot has
     *         never been computed
     */
    BillStatsResponse getStats(DashboardRange range);

    /**
     * Trigger an asynchronous recompute of the snapshot in the Lambda and acknowledge immediately.
     * The new figures land shortly after; the caller re-reads {@link #getStats} to pick them up.
     *
     * @return the snapshot's current (pre-refresh) timestamp plus an acknowledgement message
     */
    BillStatsRefreshResponse refresh();
}
