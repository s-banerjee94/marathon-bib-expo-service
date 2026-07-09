package com.timekeeper.bibexpo.service.dashboard;

import com.timekeeper.bibexpo.model.dto.response.dashboard.PlatformDashboardResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Single rollup endpoint for the ROOT/ADMIN platform overview dashboard.
 * Composes the global organization, event, user, and trend blocks into one coherent snapshot.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PlatformDashboardService {

    public static final String DASHBOARD_CACHE = "dashboardPlatform";

    private static final String CACHE_KEY = "#query.range + ':' + #query.tierRange + ':' + #query.statusRange "
            + "+ ':' + #query.citiesRange + ':' + #query.trendBuckets + ':' + #query.trendInterval "
            + "+ ':' + #query.topCities + ':' + #query.topOrgs";

    private final PlatformStatsService platformStatsService;
    private final PlatformTrendsService platformTrendsService;

    @Cacheable(value = DASHBOARD_CACHE, key = CACHE_KEY)
    public PlatformDashboardResponse loadFor(PlatformDashboardQuery query) {
        log.debug("Cache miss — computing platform dashboard for range: {}", query.getRange());
        return compute(query);
    }

    @CachePut(value = DASHBOARD_CACHE, key = CACHE_KEY)
    public PlatformDashboardResponse refreshFor(PlatformDashboardQuery query) {
        log.info("Force refresh — recomputing platform dashboard for range: {}", query.getRange());
        return compute(query);
    }

    private PlatformDashboardResponse compute(PlatformDashboardQuery query) {
        return PlatformDashboardResponse.builder()
                .refreshedAt(Instant.now())
                .range(query.getRange())
                .organizations(platformStatsService.buildOrganizationsBlock(query))
                .events(platformStatsService.buildEventsBlock(query))
                .users(platformStatsService.buildUsersBlock())
                .trends(platformTrendsService.buildTrendsBlock(query))
                .build();
    }
}
