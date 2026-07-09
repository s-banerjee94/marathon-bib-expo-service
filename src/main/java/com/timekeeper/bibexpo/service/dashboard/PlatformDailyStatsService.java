package com.timekeeper.bibexpo.service.dashboard;

import com.timekeeper.bibexpo.repository.OrganizationRepository;
import com.timekeeper.bibexpo.repository.PlatformDailyStatsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;

/**
 * Maintains the {@code platform_daily_stats} table that backs the growth trend.
 * The daily job records exact live counts going forward; {@link #backfillHistory()} seeds
 * missing historical days once from entity createdAt timestamps (activeEvents approximated).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlatformDailyStatsService {

    /** Cap how far back the one-time seed reaches; older buckets are computed live on demand. */
    private static final int MAX_BACKFILL_DAYS = 400;

    private final PlatformDailyStatsRepository platformDailyStatsRepository;
    private final PlatformLiveStatsQueries liveStatsQueries;
    private final OrganizationRepository organizationRepository;

    /** Records exact live counts for a date. Idempotent — safe to re-run for the same date. */
    @Transactional
    public void snapshotForDate(LocalDate date) {
        platformDailyStatsRepository.upsert(date,
                liveStatsQueries.countOrganizations(),
                liveStatsQueries.countTotalEvents(),
                liveStatsQueries.countActiveEvents(),
                liveStatsQueries.countTotalUsers(),
                liveStatsQueries.countDistinctCities());
        log.info("Platform daily snapshot saved — date: {}", date);
    }

    /** Seeds rows for any missing historical day (createdAt-derived). Never overwrites existing rows. */
    @Transactional
    public void backfillHistory() {
        Instant earliest = organizationRepository.findMinCreatedAt();
        if (earliest == null) {
            log.info("Platform stats backfill skipped — no organizations yet");
            return;
        }

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate yesterday = today.minusDays(1);
        LocalDate earliestDate = earliest.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate floor = today.minusDays(MAX_BACKFILL_DAYS);
        LocalDate from = earliestDate.isBefore(floor) ? floor : earliestDate;
        if (from.isAfter(yesterday)) {
            return;
        }

        Set<LocalDate> existing = new HashSet<>(platformDailyStatsRepository.findExistingDates(from, yesterday));
        int filled = 0;
        for (LocalDate d = from; !d.isAfter(yesterday); d = d.plusDays(1)) {
            if (existing.contains(d)) {
                continue;
            }
            Instant asOf = d.atTime(LocalTime.MAX).toInstant(ZoneOffset.UTC);
            platformDailyStatsRepository.upsert(d,
                    liveStatsQueries.countOrganizationsAsOf(asOf),
                    liveStatsQueries.countTotalEventsAsOf(asOf),
                    liveStatsQueries.countActiveEventsAsOf(asOf),
                    liveStatsQueries.countTotalUsersAsOf(asOf),
                    liveStatsQueries.countDistinctCitiesAsOf(asOf));
            filled++;
        }
        log.info("Platform stats backfill complete — filled {} day(s) from {} to {}", filled, from, yesterday);
    }
}
