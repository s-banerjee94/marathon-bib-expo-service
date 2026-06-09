package com.timekeeper.bibexpo.billing.model.dto;

import com.timekeeper.bibexpo.billing.model.dto.response.BillStatsResponse;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Jackson shape of the JSON blob the billing-stats Lambda writes into
 * {@code billing_stats_snapshot.snapshot_data}. The Lambda precomputes all range windows in one
 * blob so the read endpoint never has to recompute; {@code ranges} is keyed by
 * {@link com.timekeeper.bibexpo.model.enums.DashboardRange} name (ALL / YEAR / MONTH) and each
 * value is a fully-populated {@link BillStatsResponse} slice. Internal — never returned directly.
 */
@Data
@NoArgsConstructor
public class BillStatsSnapshotData {

    private String currency;
    private Instant refreshedAt;
    private String computedBy;
    private Map<String, BillStatsResponse> ranges;
}
