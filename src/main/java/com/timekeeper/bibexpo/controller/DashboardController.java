package com.timekeeper.bibexpo.controller;

import com.timekeeper.bibexpo.model.dto.response.dashboard.OrgDashboardResponse;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.enums.DashboardRange;
import com.timekeeper.bibexpo.model.enums.TrendInterval;
import com.timekeeper.bibexpo.repository.UserRepository;
import com.timekeeper.bibexpo.service.dashboard.OrgDashboardQuery;
import com.timekeeper.bibexpo.service.dashboard.OrgDashboardService;
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
    private final UserRepository userRepository;

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
        User user = userRepository.findByUsernameWithOrganization(currentUser.getUsername())
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + currentUser.getUsername()));
        if (user.getOrganization() == null) {
            throw new IllegalStateException("Your account is not assigned to an organization.");
        }
        return OrgDashboardQuery.builder()
                .orgId(user.getOrganization().getId())
                .range(range)
                .statusRange(statusRange != null ? statusRange : range)
                .citiesRange(citiesRange != null ? citiesRange : range)
                .trendBuckets(trendBuckets)
                .trendInterval(trendInterval)
                .topCities(topCities)
                .build();
    }

    private void rejectOrgIdParam(Long organizationId) {
        if (organizationId != null) {
            throw new IllegalArgumentException("organizationId query parameter is not allowed; organization is resolved from the JWT.");
        }
    }
}
