package com.timekeeper.bibexpo.service.dashboard;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Drives the platform daily-stats table: records yesterday's exact counts each night and
 * seeds missing history once on startup.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PlatformDailyStatsSnapshotJob {

    private final PlatformDailyStatsService platformDailyStatsService;

    @Scheduled(cron = "0 10 0 * * *", zone = "UTC")
    public void runDailySnapshot() {
        LocalDate yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        log.info("Platform daily snapshot job started for date: {}", yesterday);
        try {
            platformDailyStatsService.snapshotForDate(yesterday);
        } catch (Exception e) {
            log.error("Platform daily snapshot failed for date: {}", yesterday, e);
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void backfillOnStartup() {
        try {
            platformDailyStatsService.backfillHistory();
        } catch (Exception e) {
            log.error("Platform stats backfill failed on startup", e);
        }
    }
}
