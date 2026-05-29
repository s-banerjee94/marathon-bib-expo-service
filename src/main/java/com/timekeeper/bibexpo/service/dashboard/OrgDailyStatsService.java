package com.timekeeper.bibexpo.service.dashboard;

import com.timekeeper.bibexpo.repository.OrgDailyStatsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrgDailyStatsService {

    private final OrgDailyStatsRepository orgDailyStatsRepository;
    private final LiveStatsQueries liveStatsQueries;

    /**
     * Computes live counts for the given org and date, then upserts into org_daily_stats.
     * Idempotent — safe to call multiple times for the same (orgId, date).
     */
    @Transactional
    public void snapshotForDate(Long orgId, LocalDate date) {
        int totalEvents    = liveStatsQueries.countTotalEvents(orgId);
        int activeEvents   = liveStatsQueries.countActiveEvents(orgId);
        int totalUsers     = liveStatsQueries.countTotalUsers(orgId);
        int distinctCities = liveStatsQueries.countDistinctCities(orgId);

        orgDailyStatsRepository.upsert(orgId, date, totalEvents, activeEvents, totalUsers, distinctCities);
        log.info("Snapshot saved — orgId: {}, date: {}, events: {}, active: {}, users: {}, cities: {}",
                orgId, date, totalEvents, activeEvents, totalUsers, distinctCities);
    }
}
