package com.timekeeper.bibexpo.service.dashboard;

import com.timekeeper.bibexpo.model.dto.response.dashboard.TrendsDto;
import com.timekeeper.bibexpo.model.dto.response.dashboard.TrendSeriesDto;
import com.timekeeper.bibexpo.model.entity.OrgDailyStats;
import com.timekeeper.bibexpo.model.enums.TrendInterval;
import com.timekeeper.bibexpo.repository.OrgDailyStatsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TrendsService {

    private final OrgDailyStatsRepository orgDailyStatsRepository;
    private final LiveStatsQueries liveStatsQueries;

    @Transactional(readOnly = true)
    public TrendsDto buildTrendsBlock(OrgDashboardQuery query) {
        Long orgId = query.getOrgId();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        List<LocalDate> bucketStarts = resolveBucketDates(query.getTrendInterval(), query.getTrendBuckets(), today);

        LocalDate rangeFrom = bucketStarts.get(0);
        LocalDate rangeTo = today.minusDays(1);

        // Load all snapshots from first bucket start up to yesterday
        TreeMap<LocalDate, OrgDailyStats> snapshotMap = orgDailyStatsRepository
                .findByOrgAndDateRange(orgId, rangeFrom, rangeTo)
                .stream()
                .collect(Collectors.toMap(OrgDailyStats::getSnapshotDate, s -> s, (a, b) -> b, TreeMap::new));

        List<Long> events = new ArrayList<>();
        List<Long> active = new ArrayList<>();
        List<Long> users  = new ArrayList<>();
        List<Long> cities = new ArrayList<>();

        for (int i = 0; i < bucketStarts.size(); i++) {
            boolean isLastBucket = (i == bucketStarts.size() - 1);

            if (isLastBucket) {
                events.add((long) liveStatsQueries.countTotalEvents(orgId));
                active.add((long) liveStatsQueries.countActiveEvents(orgId));
                users.add((long) liveStatsQueries.countTotalUsers(orgId));
                cities.add((long) liveStatsQueries.countDistinctCities(orgId));
            } else {
                LocalDate bucketEnd = bucketEndDate(query.getTrendInterval(), bucketStarts.get(i));
                Map.Entry<LocalDate, OrgDailyStats> entry = snapshotMap.floorEntry(bucketEnd);

                if (entry != null) {
                    OrgDailyStats s = entry.getValue();
                    events.add((long) s.getTotalEvents());
                    active.add((long) s.getActiveEvents());
                    users.add((long) s.getTotalUsers());
                    cities.add((long) s.getDistinctCities());
                } else {
                    events.add(0L);
                    active.add(0L);
                    users.add(0L);
                    cities.add(0L);
                }
            }
        }

        return TrendsDto.builder()
                .interval(query.getTrendInterval())
                .buckets(query.getTrendBuckets())
                .bucketLabels(bucketStarts.stream().map(LocalDate::toString).toList())
                .series(TrendSeriesDto.builder()
                        .events(events)
                        .active(active)
                        .users(users)
                        .cities(cities)
                        .build())
                .build();
    }

    private List<LocalDate> resolveBucketDates(TrendInterval interval, int buckets, LocalDate today) {
        List<LocalDate> dates = new ArrayList<>(buckets);
        switch (interval) {
            case DAY -> {
                for (int i = buckets - 1; i >= 0; i--) dates.add(today.minusDays(i));
            }
            case WEEK -> {
                LocalDate monday = today.with(DayOfWeek.MONDAY);
                for (int i = buckets - 1; i >= 0; i--) dates.add(monday.minusWeeks(i));
            }
            case MONTH -> {
                LocalDate firstOfMonth = today.withDayOfMonth(1);
                for (int i = buckets - 1; i >= 0; i--) dates.add(firstOfMonth.minusMonths(i));
            }
        }
        return dates;
    }

    private LocalDate bucketEndDate(TrendInterval interval, LocalDate bucketStart) {
        return switch (interval) {
            case DAY   -> bucketStart;
            case WEEK  -> bucketStart.plusDays(6);
            case MONTH -> bucketStart.plusMonths(1).minusDays(1);
        };
    }
}
