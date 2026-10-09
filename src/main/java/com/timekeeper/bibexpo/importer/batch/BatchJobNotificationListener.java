package com.timekeeper.bibexpo.importer.batch;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.timekeeper.bibexpo.importer.model.dto.response.ErrorSummary;
import com.timekeeper.bibexpo.importer.model.entity.ImportJob;
import com.timekeeper.bibexpo.importer.model.enums.ImportMode;
import com.timekeeper.bibexpo.importer.repository.ImportJobRepository;
import com.timekeeper.bibexpo.notification.model.dto.NotifyRequest;
import com.timekeeper.bibexpo.notification.model.enums.NotificationAudience;
import com.timekeeper.bibexpo.notification.model.enums.NotificationType;
import com.timekeeper.bibexpo.notification.service.NotificationService;
import com.timekeeper.bibexpo.participant.api.ParticipantStore;
import com.timekeeper.bibexpo.event.api.EventQuota;
import com.timekeeper.bibexpo.participant.service.EventGoodieService;
import com.timekeeper.bibexpo.participant.service.ParticipantStatisticsService;
import com.timekeeper.bibexpo.user.api.UserDirectory;
import com.timekeeper.bibexpo.user.model.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.JobParameters;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class BatchJobNotificationListener implements JobExecutionListener {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private final NotificationService notificationService;
    private final ImportJobRepository importJobRepository;
    private final ParticipantStore participantStore;
    private final EventGoodieService eventGoodieService;
    private final UserDirectory userDirectory;
    private final ParticipantStatisticsService participantStatisticsService;
    private final ObjectMapper objectMapper;
    private final EventQuota eventQuota;

    @Override
    public void beforeJob(JobExecution jobExecution) {
        String eventIdParam = jobExecution.getJobParameters().getString("eventId");
        if (eventIdParam == null) return;

        // ADD_ON appends to the existing roster; only a full IMPORT wipes first.
        if (ImportMode.ADD_ON.name().equals(jobExecution.getJobParameters().getString("mode"))) {
            return;
        }

        try {
            int deleted = participantStore.deleteAllByEventId(eventIdParam);
            log.atInfo()
                    .addKeyValue("event", "import.roster_cleared")
                    .addKeyValue("eventId", eventIdParam)
                    .addKeyValue("deleted", deleted)
                    .log("Roster cleared before a full import");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to clear existing participants before import.", e);
        }
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        deleteTempFile(jobExecution);

        JobParameters params = jobExecution.getJobParameters();
        String userIdParam = params.getString("uploadedByUserId");
        String eventIdParam = params.getString("eventId");
        if (userIdParam == null || eventIdParam == null) {
            return;
        }

        Long userId = Long.parseLong(userIdParam);
        Long eventId = Long.parseLong(eventIdParam);
        String eventName = params.getString("eventName", "");
        String jobStatus = jobExecution.getStatus().toString();

        int writeCount = 0;
        int skipCount = 0;
        int readCount = 0;
        int readSkipCount = 0;
        if (!jobExecution.getStepExecutions().isEmpty()) {
            var step = jobExecution.getStepExecutions().iterator().next();
            writeCount = (int) step.getWriteCount();
            skipCount = (int) step.getSkipCount();
            readCount = (int) step.getReadCount();
            readSkipCount = (int) step.getReadSkipCount();
            // The reader drops repeated bibs and chips itself, so Spring Batch never counts them.
            int duplicateRows = step.getExecutionContext().getInt(CsvItemReader.DUPLICATE_ROWS_KEY, 0);
            readSkipCount += duplicateRows;
            skipCount += duplicateRows;
        }

        List<String> goodiesColumns = goodiesColumns(jobExecution);

        try {
            if (!goodiesColumns.isEmpty()) {
                eventGoodieService.addImportedGoodies(eventId, goodiesColumns);
            }
        } catch (Exception e) {
            logFailure("import.goodies_update_failed", eventId, jobExecution, e);
        }

        try {
            saveImportJob(jobExecution, eventId, userId, jobStatus, goodiesColumns,
                    writeCount, skipCount, readCount, readSkipCount);
        } catch (Exception e) {
            logFailure("import.job_save_failed", eventId, jobExecution, e);
        }

        log.atInfo()
                .addKeyValue("event", "import.finished")
                .addKeyValue("eventId", eventId)
                .addKeyValue("importId", params.getString("importId"))
                .addKeyValue("status", jobStatus)
                .addKeyValue("read", readCount + readSkipCount)
                .addKeyValue("written", writeCount)
                .addKeyValue("skipped", skipCount)
                .log("Import finished");

        if ("COMPLETED".equals(jobStatus)) {
            User uploader = userDirectory.findById(userId).orElse(null);
            reconcileStats(eventId, uploader, jobExecution);
            notifyOrgAdminsOfImport(uploader, eventId, eventName, writeCount, skipCount);
        }

        sendImportNotification(userId, eventId, eventName, jobStatus, writeCount, skipCount);
    }

    private List<String> goodiesColumns(JobExecution jobExecution) {
        String json = jobExecution.getExecutionContext().getString(CsvItemReader.GOODIES_COLUMNS_KEY, null);
        return json == null ? List.of() : objectMapper.readValue(json, STRING_LIST);
    }

    /**
     * On a successful import, also inform the organization's admin(s). The uploader is passed as the
     * actor so an admin who ran the import themselves is excluded here and only gets their own
     * {@code USER} notification — no duplicate.
     */
    private void notifyOrgAdminsOfImport(User uploader, Long eventId, String eventName, int writeCount, int skipCount) {
        if (uploader == null || uploader.getOrganization() == null) {
            return;
        }
        notificationService.notify(NotifyRequest.builder()
                .audience(NotificationAudience.ORGANIZATION_ADMINS)
                .organizationId(uploader.getOrganization().getId())
                .type(NotificationType.IMPORT_COMPLETED)
                .title("CSV Import Completed")
                .message(String.format("%s imported %d participant(s) for %s. %d row(s) skipped.",
                        uploader.getUsername(), writeCount, eventName, skipCount))
                .entityType("EVENT")
                .entityId(String.valueOf(eventId))
                .actor(uploader)
                .build());
    }

    private void sendImportNotification(Long userId, Long eventId, String eventName, String jobStatus,
                                        int writeCount, int skipCount) {
        boolean completed = "COMPLETED".equals(jobStatus);
        String title = completed ? "CSV Import Completed" : "CSV Import Failed";
        String message = completed
                ? String.format("Successfully imported %d participant(s) for %s. %d row(s) skipped.",
                        writeCount, eventName, skipCount)
                : String.format("The import for %s ended with status %s. %d row(s) written, %d skipped.",
                        eventName, jobStatus, writeCount, skipCount);

        notificationService.notify(NotifyRequest.builder()
                .audience(NotificationAudience.USER)
                .targetUserId(userId)
                .type(completed ? NotificationType.IMPORT_COMPLETED : NotificationType.IMPORT_FAILED)
                .title(title)
                .message(message)
                .entityType("EVENT")
                .entityId(String.valueOf(eventId))
                .build());
    }

    private void reconcileStats(Long eventId, User user, JobExecution jobExecution) {
        if (user == null) {
            log.atWarn()
                    .addKeyValue("event", "import.stats_reconcile_skipped")
                    .addKeyValue("eventId", eventId)
                    .addKeyValue("reason", "uploader_not_found")
                    .log("Stats not reconciled after import");
            return;
        }
        try {
            participantStatisticsService.reconcile(eventId, user);
        } catch (Exception e) {
            logFailure("import.stats_reconcile_failed", eventId, jobExecution, e);
        }
    }

    private void saveImportJob(JobExecution jobExecution, Long eventId, Long userId, String jobStatus,
                               List<String> goodiesColumns,
                               int writeCount, int skipCount, int readCount, int readSkipCount) {
        JobParameters params = jobExecution.getJobParameters();

        String errorSummaryJson;
        if ("STOPPED".equals(jobStatus)) {
            errorSummaryJson = "{\"reason\":\"Stopped by user\"}";
        } else {
            // Only validationErrors is reliably tracked; other breakdown fields are unknown from Spring Batch counters
            errorSummaryJson = objectMapper.writeValueAsString(ErrorSummary.builder().validationErrors(skipCount).build());
        }

        ImportJob.ImportStatus status = "COMPLETED".equals(jobStatus)
                ? ImportJob.ImportStatus.COMPLETED
                : ImportJob.ImportStatus.FAILED;

        ImportMode mode = ImportMode.ADD_ON.name().equals(params.getString("mode")) ? ImportMode.ADD_ON : ImportMode.IMPORT;

        // The row created at launch is updated in place. It must keep its id for the whole run:
        // the error rows reference it, and replacing it would cascade them away.
        String importId = params.getString("importId");
        ImportJob importJob = importJobRepository.findById(importId).orElseGet(() ->
                ImportJob.builder().importId(importId).build());

        importJob.setJobExecutionId(jobExecution.getId());
        importJob.setEventId(eventId);
        importJob.setEventName(params.getString("eventName", ""));
        importJob.setFileName(params.getString("fileName", ""));
        importJob.setTotalRows(readCount + readSkipCount);
        importJob.setSuccessCount(writeCount);
        importJob.setFailureCount(skipCount);
        importJob.setStatus(status);
        importJob.setMode(mode);
        importJob.setErrorSummary(errorSummaryJson);
        importJob.setGoodiesDetected(goodiesColumns.isEmpty() ? null : objectMapper.writeValueAsString(goodiesColumns));
        importJob.setImportedBy(userId);

        importJobRepository.save(importJob);

        // The allowance is spent here rather than at launch, so a failed or abandoned run costs
        // the customer nothing.
        if (status == ImportJob.ImportStatus.COMPLETED) {
            if (mode == ImportMode.ADD_ON) {
                eventQuota.recordAddOnImport(eventId);
            } else {
                eventQuota.recordFullImport(eventId);
            }
        }
    }

    private void logFailure(String event, Long eventId, JobExecution jobExecution, Exception e) {
        log.atError()
                .setCause(e)
                .addKeyValue("event", event)
                .addKeyValue("eventId", eventId)
                .addKeyValue("importId", jobExecution.getJobParameters().getString("importId"))
                .log("Post-import step failed");
    }

    private void deleteTempFile(JobExecution jobExecution) {
        deleteIfPresent(jobExecution.getJobParameters().getString("filePath"));
        deleteIfPresent(jobExecution.getJobParameters().getString("mappingPath"));
    }

    private void deleteIfPresent(String path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(Paths.get(path));
        } catch (IOException e) {
            log.atWarn()
                    .setCause(e)
                    .addKeyValue("event", "import.temp_file_left")
                    .addKeyValue("path", path)
                    .log("Temp file could not be deleted");
        }
    }
}
