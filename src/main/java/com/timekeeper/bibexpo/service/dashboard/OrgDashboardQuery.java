package com.timekeeper.bibexpo.service.dashboard;

import com.timekeeper.bibexpo.model.enums.DashboardRange;
import com.timekeeper.bibexpo.model.enums.TrendInterval;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrgDashboardQuery {
    private Long orgId;
    private DashboardRange range;
    /** Overrides {@code range} for events.byStatus only; null falls back to {@code range}. */
    private DashboardRange statusRange;
    /** Overrides {@code range} for events.byCity and events.distinctCities only; null falls back to {@code range}. */
    private DashboardRange citiesRange;
    private int trendBuckets;
    private TrendInterval trendInterval;
    private int topCities;
}
