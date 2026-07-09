package com.timekeeper.bibexpo.controller;

import com.timekeeper.bibexpo.model.dto.response.dashboard.PlatformDashboardResponse;
import com.timekeeper.bibexpo.model.dto.response.dashboard.PlatformRevenueResponse;
import com.timekeeper.bibexpo.model.enums.DashboardRange;
import com.timekeeper.bibexpo.model.enums.TrendInterval;
import com.timekeeper.bibexpo.service.dashboard.PlatformDashboardQuery;
import com.timekeeper.bibexpo.service.dashboard.PlatformDashboardService;
import com.timekeeper.bibexpo.service.dashboard.PlatformRevenueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for the global ROOT/ADMIN platform overview dashboard.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class PlatformDashboardController implements PlatformDashboardControllerApi {

    private final PlatformDashboardService platformDashboardService;
    private final PlatformRevenueService platformRevenueService;

    @Override
    public ResponseEntity<PlatformDashboardResponse> getDashboard(
            DashboardRange range, DashboardRange tierRange, DashboardRange statusRange, DashboardRange citiesRange,
            int trendBuckets, TrendInterval trendInterval, int topCities, int topOrgs, Long organizationId) {
        rejectOrgIdParam(organizationId);
        PlatformDashboardQuery query = buildQuery(range, tierRange, statusRange, citiesRange,
                trendBuckets, trendInterval, topCities, topOrgs);
        log.info("GET /dashboard/platform — range: {}", range);
        return ResponseEntity.ok(platformDashboardService.loadFor(query));
    }

    @Override
    public ResponseEntity<PlatformDashboardResponse> refreshDashboard(
            DashboardRange range, DashboardRange tierRange, DashboardRange statusRange, DashboardRange citiesRange,
            int trendBuckets, TrendInterval trendInterval, int topCities, int topOrgs, Long organizationId) {
        rejectOrgIdParam(organizationId);
        PlatformDashboardQuery query = buildQuery(range, tierRange, statusRange, citiesRange,
                trendBuckets, trendInterval, topCities, topOrgs);
        log.info("POST /dashboard/platform/refresh — range: {}", range);
        return ResponseEntity.ok(platformDashboardService.refreshFor(query));
    }

    @Override
    public ResponseEntity<PlatformRevenueResponse> getRevenue(
            DashboardRange range, int trendBuckets, TrendInterval trendInterval) {
        log.info("GET /dashboard/platform/revenue — range: {}", range);
        return ResponseEntity.ok(platformRevenueService.buildRevenue(range, clamp(trendBuckets, 1, 90), trendInterval));
    }

    private PlatformDashboardQuery buildQuery(DashboardRange range, DashboardRange tierRange,
                                              DashboardRange statusRange, DashboardRange citiesRange,
                                              int trendBuckets, TrendInterval trendInterval,
                                              int topCities, int topOrgs) {
        return PlatformDashboardQuery.builder()
                .range(range)
                .tierRange(tierRange != null ? tierRange : range)
                .statusRange(statusRange != null ? statusRange : range)
                .citiesRange(citiesRange != null ? citiesRange : range)
                .trendBuckets(clamp(trendBuckets, 1, 90))
                .trendInterval(trendInterval)
                .topCities(clamp(topCities, 1, 20))
                .topOrgs(clamp(topOrgs, 1, 20))
                .build();
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void rejectOrgIdParam(Long organizationId) {
        if (organizationId != null) {
            throw new IllegalArgumentException("organizationId is not a valid parameter for the platform dashboard; it is always global.");
        }
    }
}
