package com.timekeeper.bibexpo.reporting.service;

import com.timekeeper.bibexpo.reporting.repository.OrgDailyStatsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
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
    }
}
