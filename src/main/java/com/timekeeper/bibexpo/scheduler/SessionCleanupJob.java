package com.timekeeper.bibexpo.scheduler;

import com.timekeeper.bibexpo.repository.ActiveSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Daily housekeeping of the {@code active_sessions} table.
 * Removes rows whose refresh window has already lapsed so the table stays small.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SessionCleanupJob {

    private final ActiveSessionRepository activeSessionRepository;

    @Scheduled(cron = "0 0 3 * * *") // every day at 03:00 server time
    @Transactional
    public void purgeExpiredSessions() {
        int deleted = activeSessionRepository.deleteAllExpired(Instant.now());
        if (deleted > 0) {
            log.info("Session cleanup removed {} expired session row(s)", deleted);
        }
    }
}
