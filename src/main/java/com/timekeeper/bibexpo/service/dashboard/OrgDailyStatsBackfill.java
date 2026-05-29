package com.timekeeper.bibexpo.service.dashboard;

import com.timekeeper.bibexpo.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrgDailyStatsBackfill implements ApplicationRunner {

    private final OrganizationRepository organizationRepository;
    private final OrgDailyStatsService orgDailyStatsService;

    @Value("${dashboard.backfill.enabled:false}")
    private boolean enabled;

    @Value("${dashboard.backfill.days:90}")
    private int backfillDays;

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) return;

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        log.warn("Dashboard backfill started — filling {} days of history. Counts reflect current state, not historical.", backfillDays);

        for (int i = backfillDays; i >= 1; i--) {
            LocalDate date = today.minusDays(i);
            organizationRepository.findAllActiveIds().forEach(orgId -> {
                try {
                    orgDailyStatsService.snapshotForDate(orgId, date);
                } catch (Exception e) {
                    log.error("Backfill failed — orgId: {}, date: {}", orgId, date, e);
                }
            });
        }

        log.warn("Dashboard backfill complete — set dashboard.backfill.enabled=false and restart.");
    }
}
