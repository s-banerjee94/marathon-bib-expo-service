package com.timekeeper.bibexpo.service.dashboard;

import com.timekeeper.bibexpo.model.dto.response.dashboard.PlatformTrendSeriesDto;
import com.timekeeper.bibexpo.model.dto.response.dashboard.PlatformTrendsDto;
import com.timekeeper.bibexpo.model.entity.PlatformDailyStats;
import com.timekeeper.bibexpo.repository.PlatformDailyStatsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Builds the platform growth trend: cumulative end-of-bucket counts, oldest first, with the
 * last bucket reflecting live "today" values. Past buckets are read from the daily snapshot
 * table; any bucket without a snapshot falls back to createdAt-derived counts so the chart is
 * always populated.
 */
@Service
@RequiredArgsConstructor
public class PlatformTrendsService {

    private final PlatformDailyStatsRepository platformDailyStatsRepository;
    private final PlatformLiveStatsQueries liveStatsQueries;

    @Transactional(readOnly = true)
    public PlatformTrendsDto buildTrendsBlock(PlatformDashboardQuery query) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        List<LocalDate> bucketStarts = TrendBucketUtil.bucketStarts(query.getTrendInterval(), query.getTrendBuckets(), today);

        LocalDate rangeFrom = bucketStarts.get(0);
        TreeMap<LocalDate, PlatformDailyStats> snapshots = platformDailyStatsRepository
                .findByDateRange(rangeFrom, today.minusDays(1))
                .stream()
                .collect(Collectors.toMap(PlatformDailyStats::getSnapshotDate, s -> s, (a, b) -> b, TreeMap::new));

        List<Long> organizations = new ArrayList<>();
        List<Long> events = new ArrayList<>();
        List<Long> users  = new ArrayList<>();
        List<Long> active = new ArrayList<>();
        List<Long> cities = new ArrayList<>();

        for (int i = 0; i < bucketStarts.size(); i++) {
            if (i == bucketStarts.size() - 1) {
                organizations.add((long) liveStatsQueries.countOrganizations());
                events.add((long) liveStatsQueries.countTotalEvents());
                users.add((long) liveStatsQueries.countTotalUsers());
                active.add((long) liveStatsQueries.countActiveEvents());
                cities.add((long) liveStatsQueries.countDistinctCities());
                continue;
            }

            LocalDate bucketEnd = TrendBucketUtil.bucketEnd(query.getTrendInterval(), bucketStarts.get(i));
            PlatformDailyStats snapshot = floor(snapshots, bucketEnd);
            if (snapshot != null) {
                organizations.add((long) snapshot.getOrganizations());
                events.add((long) snapshot.getTotalEvents());
                users.add((long) snapshot.getTotalUsers());
                active.add((long) snapshot.getActiveEvents());
                cities.add((long) snapshot.getDistinctCities());
            } else {
                var asOf = bucketEnd.atTime(LocalTime.MAX).toInstant(ZoneOffset.UTC);
                organizations.add((long) liveStatsQueries.countOrganizationsAsOf(asOf));
                events.add((long) liveStatsQueries.countTotalEventsAsOf(asOf));
                users.add((long) liveStatsQueries.countTotalUsersAsOf(asOf));
                active.add((long) liveStatsQueries.countActiveEventsAsOf(asOf));
                cities.add((long) liveStatsQueries.countDistinctCitiesAsOf(asOf));
            }
        }

        return PlatformTrendsDto.builder()
                .interval(query.getTrendInterval())
                .buckets(query.getTrendBuckets())
                .bucketLabels(bucketStarts.stream().map(d -> TrendBucketUtil.label(query.getTrendInterval(), d)).toList())
                .series(PlatformTrendSeriesDto.builder()
                        .organizations(organizations)
                        .events(events)
                        .users(users)
                        .activeEvents(active)
                        .cities(cities)
                        .build())
                .build();
    }

    private PlatformDailyStats floor(TreeMap<LocalDate, PlatformDailyStats> snapshots, LocalDate bucketEnd) {
        var entry = snapshots.floorEntry(bucketEnd);
        return entry != null ? entry.getValue() : null;
    }
}
