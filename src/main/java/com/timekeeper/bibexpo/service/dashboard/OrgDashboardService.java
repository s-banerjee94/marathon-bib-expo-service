package com.timekeeper.bibexpo.service.dashboard;

import com.timekeeper.bibexpo.model.dto.response.dashboard.OrgDashboardResponse;
import com.timekeeper.bibexpo.model.dto.response.dashboard.OrgInfoDto;
import com.timekeeper.bibexpo.repository.OrganizationRepository;
import com.timekeeper.bibexpo.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrgDashboardService {

    public static final String DASHBOARD_CACHE = "dashboardOrg";

    private static final String CACHE_KEY = "#query.orgId + ':' + #query.range + ':' + #query.statusRange "
            + "+ ':' + #query.citiesRange + ':' + #query.trendBuckets + ':' + #query.trendInterval + ':' + #query.topCities";

    private final OrganizationRepository organizationRepository;
    private final EventStatsService eventStatsService;
    private final UserStatsService userStatsService;
    private final TrendsService trendsService;
    private final StorageService storageService;

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
        return OrgDashboardResponse.builder()
                .refreshedAt(Instant.now())
                .range(query.getRange())
                .organization(loadOrgInfo(query.getOrgId()))
                .events(eventStatsService.buildEventsBlock(query))
                .users(userStatsService.buildUsersBlock(query.getOrgId()))
                .trends(trendsService.buildTrendsBlock(query))
                .build();
    }

    private OrgInfoDto loadOrgInfo(Long orgId) {
        return organizationRepository.findByIdAndDeletedFalse(orgId)
                .map(org -> {
                    OrgInfoDto dto = OrgInfoDto.fromEntity(org);
                    dto.setLogoUrl(storageService.createDownloadUrl(org.getLogoKey()));
                    return dto;
                })
                .orElseThrow(() -> new IllegalStateException("Organization not found for id: " + orgId));
    }
}
