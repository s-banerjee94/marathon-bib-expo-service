package com.timekeeper.bibexpo.reporting.controller;

import com.timekeeper.bibexpo.reporting.model.dto.response.OrgDashboardResponse;
import com.timekeeper.bibexpo.shared.model.enums.DashboardRange;
import com.timekeeper.bibexpo.reporting.model.enums.TrendInterval;
import com.timekeeper.bibexpo.reporting.service.DashboardQueryLimits;
import com.timekeeper.bibexpo.reporting.service.OrgDashboardQuery;
import com.timekeeper.bibexpo.reporting.service.OrgDashboardService;
import com.timekeeper.bibexpo.user.model.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for the organizer dashboard rollup endpoint.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class DashboardController implements DashboardControllerApi {

    private final OrgDashboardService orgDashboardService;

    @Override
    public ResponseEntity<OrgDashboardResponse> getDashboard(
            DashboardRange range, DashboardRange statusRange, DashboardRange citiesRange,
            int trendBuckets, TrendInterval trendInterval,
            int topCities, Long organizationId, User currentUser) {
        rejectOrgIdParam(organizationId);
        OrgDashboardQuery query = buildQuery(currentUser, range, statusRange, citiesRange, trendBuckets, trendInterval, topCities);
        log.info("GET /dashboard/organization — orgId: {} range: {}", query.getOrgId(), range);
        return ResponseEntity.ok(orgDashboardService.loadFor(query));
    }

    @Override
    public ResponseEntity<OrgDashboardResponse> refreshDashboard(
            DashboardRange range, DashboardRange statusRange, DashboardRange citiesRange,
            int trendBuckets, TrendInterval trendInterval,
            int topCities, Long organizationId, User currentUser) {
        rejectOrgIdParam(organizationId);
        OrgDashboardQuery query = buildQuery(currentUser, range, statusRange, citiesRange, trendBuckets, trendInterval, topCities);
        log.info("POST /dashboard/organization/refresh — orgId: {} range: {}", query.getOrgId(), range);
        return ResponseEntity.ok(orgDashboardService.refreshFor(query));
    }

    private OrgDashboardQuery buildQuery(User currentUser, DashboardRange range,
                                         DashboardRange statusRange, DashboardRange citiesRange,
                                         int trendBuckets, TrendInterval trendInterval, int topCities) {
        if (currentUser.getOrganization() == null) {
            throw new IllegalStateException("Your account is not assigned to an organization.");
        }
        return OrgDashboardQuery.builder()
                .orgId(currentUser.getOrganization().getId())
                .range(range)
                .statusRange(statusRange != null ? statusRange : range)
                .citiesRange(citiesRange != null ? citiesRange : range)
                .trendBuckets(DashboardQueryLimits.trendBuckets(trendBuckets))
                .trendInterval(trendInterval)
                .topCities(DashboardQueryLimits.topN(topCities))
                .build();
    }

    private void rejectOrgIdParam(Long organizationId) {
        if (organizationId != null) {
            throw new IllegalArgumentException("organizationId query parameter is not allowed; organization is resolved from the JWT.");
        }
    }
}
