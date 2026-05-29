package com.timekeeper.bibexpo.service.dashboard;

import com.timekeeper.bibexpo.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrgDailyStatsSnapshotJob {

    private final OrganizationRepository organizationRepository;
    private final OrgDailyStatsService orgDailyStatsService;

    @Scheduled(cron = "0 5 0 * * *", zone = "UTC")
    public void runDailySnapshot() {
        LocalDate yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        log.info("Daily snapshot job started for date: {}", yesterday);

        organizationRepository.findAllActiveIds().forEach(orgId -> {
            try {
                orgDailyStatsService.snapshotForDate(orgId, yesterday);
            } catch (Exception e) {
                log.error("Snapshot failed — orgId: {}, date: {}", orgId, yesterday, e);
            }
        });

        log.info("Daily snapshot job completed for date: {}", yesterday);
    }
}
