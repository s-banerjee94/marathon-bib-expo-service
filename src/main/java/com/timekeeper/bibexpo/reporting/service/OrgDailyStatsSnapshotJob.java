package com.timekeeper.bibexpo.reporting.service;

import com.timekeeper.bibexpo.reporting.repository.ReportingOrganizationRepository;
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

    private final ReportingOrganizationRepository reportingOrganizationRepository;
    private final OrgDailyStatsService orgDailyStatsService;

    @Scheduled(cron = "0 5 0 * * *", zone = "UTC")
    public void runDailySnapshot() {
        LocalDate yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        int saved = 0;
        int failed = 0;
        for (Long orgId : reportingOrganizationRepository.findAllActiveIds()) {
            try {
                orgDailyStatsService.snapshotForDate(orgId, yesterday);
                saved++;
            } catch (Exception e) {
                failed++;
                log.atError().addKeyValue("event", "reporting.org_snapshot.failed").addKeyValue("orgId", orgId)
                        .addKeyValue("date", yesterday).setCause(e).log("Organization daily snapshot failed");
            }
        }
        log.atInfo().addKeyValue("event", "reporting.org_snapshot.completed").addKeyValue("date", yesterday)
                .addKeyValue("saved", saved).addKeyValue("failed", failed).log("Organization daily snapshots done");
    }
}
