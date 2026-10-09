package com.timekeeper.bibexpo.importer.batch;

import com.timekeeper.bibexpo.importer.model.entity.ImportJob;
import com.timekeeper.bibexpo.importer.repository.ImportJobRepository;
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

        for (ImportJob job : stuck) {
            log.atWarn()
                    .addKeyValue("event", "import.timed_out")
                    .addKeyValue("eventId", job.getEventId())
                    .addKeyValue("importId", job.getImportId())
                    .addKeyValue("maxMinutes", maxDurationMinutes)
                    .log("Import exceeded its maximum duration and was failed");
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
            return;
        }
        try {
            jobOperator.stop(executionId);
        } catch (Exception e) {
            log.atWarn()
                    .setCause(e)
                    .addKeyValue("event", "import.stop_failed")
                    .addKeyValue("eventId", job.getEventId())
                    .addKeyValue("jobExecutionId", executionId)
                    .log("Import stop could not be signalled");
        }
    }
}
