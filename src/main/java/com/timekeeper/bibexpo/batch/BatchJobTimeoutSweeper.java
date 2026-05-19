package com.timekeeper.bibexpo.batch;

import com.timekeeper.bibexpo.model.entity.ImportJob;
import com.timekeeper.bibexpo.repository.ImportJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class BatchJobTimeoutSweeper {

    private final ImportJobRepository importJobRepository;
    private final JobOperator jobOperator;

    @Value("${batch.import.max-duration-minutes:120}")
    private long maxDurationMinutes;

    @Scheduled(
            fixedDelayString = "${batch.import.sweeper-interval-ms:900000}",
            initialDelayString = "${batch.import.sweeper-initial-delay-ms:300000}"
    )
    public void sweep() {
        Duration maxDuration = Duration.ofMinutes(maxDurationMinutes);
        Instant threshold = Instant.now().minus(maxDuration);

        List<ImportJob> stuck = importJobRepository.findByStatus(ImportJob.ImportStatus.IN_PROGRESS).stream()
                .filter(j -> j.getImportedAt() != null && j.getImportedAt().isBefore(threshold))
                .toList();

        if (stuck.isEmpty()) {
            return;
        }
        log.warn("Sweeper found {} import job(s) exceeding {} minutes", stuck.size(), maxDurationMinutes);

        for (ImportJob job : stuck) {
            stopSpringBatchExecution(job);
            job.setStatus(ImportJob.ImportStatus.FAILED);
            job.setErrorSummary(
                    "{\"reason\":\"Exceeded maximum import duration of " + maxDurationMinutes + " minutes\"}");
        }
        importJobRepository.saveAll(stuck);
    }

    // Cooperative stop — Spring Batch checks terminateOnly between chunks. Thread releases naturally.
    // We mark the ImportJob row FAILED regardless so the event lock is freed immediately.
    private void stopSpringBatchExecution(ImportJob job) {
        Long executionId = job.getJobExecutionId();
        if (executionId == null) {
            log.warn("Import job {} has no jobExecutionId; cannot signal Spring Batch to stop", job.getImportId());
            return;
        }
        try {
            boolean stopped = jobOperator.stop(executionId);
            log.info("Requested stop on jobExecutionId={} for import {} (result={})",
                    executionId, job.getImportId(), stopped);
        } catch (Exception e) {
            log.warn("Failed to stop Spring Batch execution {} for import {}: {}",
                    executionId, job.getImportId(), e.getMessage());
        }
    }
}
