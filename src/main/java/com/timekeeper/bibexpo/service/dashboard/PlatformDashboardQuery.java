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
public class PlatformDashboardQuery {

    /** Primary range — drives KPI numbers and the range-able doughnuts. */
    private DashboardRange range;
    /** Overrides {@code range} for organizations.byTier only; null falls back to {@code range}. */
    private DashboardRange tierRange;
    /** Overrides {@code range} for organizations.byStatus and events.byStatus; null falls back to {@code range}. */
    private DashboardRange statusRange;
    /** Overrides {@code range} for events.byCity and events.distinctCities; null falls back to {@code range}. */
    private DashboardRange citiesRange;
    private int trendBuckets;
    private TrendInterval trendInterval;
    private int topCities;
    private int topOrgs;
}
