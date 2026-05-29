package com.timekeeper.bibexpo.service.dashboard;

import com.timekeeper.bibexpo.model.dto.response.dashboard.EventsDashboardDto;
import com.timekeeper.bibexpo.model.dto.response.dashboard.OrgDashboardResponse;
import com.timekeeper.bibexpo.model.dto.response.dashboard.OrgInfoDto;
import com.timekeeper.bibexpo.model.dto.response.dashboard.TrendsDto;
import com.timekeeper.bibexpo.model.dto.response.dashboard.UserCountsDto;
import com.timekeeper.bibexpo.repository.OrganizationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
@Slf4j
public class OrgDashboardService {

    public static final String DASHBOARD_CACHE = "dashboardOrg";

    private static final String CACHE_KEY = "#query.orgId + ':' + #query.range + ':' + #query.statusRange "
            + "+ ':' + #query.citiesRange + ':' + #query.trendBuckets + ':' + #query.trendInterval + ':' + #query.topCities";

    private final OrganizationRepository organizationRepository;
    private final EventStatsService eventStatsService;
    private final UserStatsService userStatsService;
    private final TrendsService trendsService;
    private final Executor dashboardTaskExecutor;

    public OrgDashboardService(OrganizationRepository organizationRepository,
                               EventStatsService eventStatsService,
                               UserStatsService userStatsService,
                               TrendsService trendsService,
                               @Qualifier("dashboardTaskExecutor") Executor dashboardTaskExecutor) {
        this.organizationRepository = organizationRepository;
        this.eventStatsService = eventStatsService;
        this.userStatsService = userStatsService;
        this.trendsService = trendsService;
        this.dashboardTaskExecutor = dashboardTaskExecutor;
    }

    @Cacheable(value = DASHBOARD_CACHE, key = CACHE_KEY)
    public OrgDashboardResponse loadFor(OrgDashboardQuery query) {
        log.debug("Cache miss — computing dashboard for orgId: {}", query.getOrgId());
        return compute(query);
    }

    @CachePut(value = DASHBOARD_CACHE, key = CACHE_KEY)
    public OrgDashboardResponse refreshFor(OrgDashboardQuery query) {
        log.info("Force refresh — recomputing dashboard for orgId: {}", query.getOrgId());
        return compute(query);
    }

    private OrgDashboardResponse compute(OrgDashboardQuery query) {
        CompletableFuture<OrgInfoDto> orgFuture = CompletableFuture.supplyAsync(
                () -> loadOrgInfo(query.getOrgId()), dashboardTaskExecutor);

        CompletableFuture<EventsDashboardDto> eventsFuture = CompletableFuture.supplyAsync(
                () -> eventStatsService.buildEventsBlock(query), dashboardTaskExecutor);

        CompletableFuture<UserCountsDto> usersFuture = CompletableFuture.supplyAsync(
                () -> userStatsService.buildUsersBlock(query.getOrgId()), dashboardTaskExecutor);

        CompletableFuture<TrendsDto> trendsFuture = CompletableFuture.supplyAsync(
                () -> trendsService.buildTrendsBlock(query), dashboardTaskExecutor);

        CompletableFuture.allOf(orgFuture, eventsFuture, usersFuture, trendsFuture).join();

        return OrgDashboardResponse.builder()
                .refreshedAt(Instant.now())
                .range(query.getRange())
                .organization(orgFuture.join())
                .events(eventsFuture.join())
                .users(usersFuture.join())
                .trends(trendsFuture.join())
                .build();
    }

    private OrgInfoDto loadOrgInfo(Long orgId) {
        return organizationRepository.findByIdAndDeletedFalse(orgId)
                .map(OrgInfoDto::fromEntity)
                .orElseThrow(() -> new IllegalStateException("Organization not found for id: " + orgId));
    }
}
