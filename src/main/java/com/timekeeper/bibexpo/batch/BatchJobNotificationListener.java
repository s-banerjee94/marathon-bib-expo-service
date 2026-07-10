package com.timekeeper.bibexpo.batch;

import tools.jackson.databind.ObjectMapper;
import com.timekeeper.bibexpo.notification.model.dto.NotifyRequest;
import com.timekeeper.bibexpo.model.dto.response.ErrorSummary;
import com.timekeeper.bibexpo.model.entity.Event;
import com.timekeeper.bibexpo.model.entity.EventLatestImport;
import com.timekeeper.bibexpo.model.entity.ImportJob;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.enums.ImportMode;
import com.timekeeper.bibexpo.notification.model.enums.NotificationAudience;
import com.timekeeper.bibexpo.notification.model.enums.NotificationType;
import com.timekeeper.bibexpo.model.entity.EventLimit;
import com.timekeeper.bibexpo.repository.EventLatestImportRepository;
import com.timekeeper.bibexpo.repository.EventLimitRepository;
import com.timekeeper.bibexpo.repository.EventRepository;
import com.timekeeper.bibexpo.repository.ImportJobRepository;
import com.timekeeper.bibexpo.repository.UserRepository;
import com.timekeeper.bibexpo.repository.dynamodb.ParticipantDDBRepository;
import com.timekeeper.bibexpo.service.EventStatsService;
import com.timekeeper.bibexpo.notification.service.NotificationService;
import com.timekeeper.bibexpo.service.util.RaceCategoryNameResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class BatchJobNotificationListener implements JobExecutionListener {

    private final NotificationService notificationService;
    private final ImportJobRepository importJobRepository;
    private final EventLatestImportRepository eventLatestImportRepository;
    private final ParticipantDDBRepository participantDDBRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final EventStatsService eventStatsService;
    private final ObjectMapper objectMapper;
    private final EventLimitRepository eventLimitRepository;
    private final RaceCategoryNameResolver nameResolver;

    @Override
    public void beforeJob(JobExecution jobExecution) {
        String eventIdParam = jobExecution.getJobParameters().getString("eventId");
        if (eventIdParam == null) return;

        // ADD_ON appends to the existing roster; only a full IMPORT wipes first.
        if (ImportMode.ADD_ON.name().equals(jobExecution.getJobParameters().getString("mode"))) {
            log.info("ADD_ON run for event {} — keeping existing participants (no wipe)", eventIdParam);
            return;
        }

        try {
            int deleted = participantDDBRepository.deleteAllByEventId(eventIdParam);
            log.info("Deleted {} existing participants for event {} before import", deleted, eventIdParam);
        } catch (Exception e) {
            log.error("Failed to delete existing participants for event {} — aborting import", eventIdParam, e);
            throw new RuntimeException("Failed to clear existing participants before import.", e);
        }
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        deleteTempFile(jobExecution);

        String userIdParam = jobExecution.getJobParameters().getString("uploadedByUserId");
        String eventIdParam = jobExecution.getJobParameters().getString("eventId");

        if (userIdParam == null || eventIdParam == null) {
            log.warn("Missing uploadedByUserId or eventId in job parameters, skipping notification");
            return;
        }

        Long userId = Long.parseLong(userIdParam);
        Long eventId = Long.parseLong(eventIdParam);

        // The import may have auto-created races/categories, so drop the cached name maps for the event.
        nameResolver.evict(eventId);

        Long jobExecutionId = jobExecution.getId();
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
        }

        try {
            updateEventGoodies(eventId, jobExecution);
        } catch (Exception e) {
            log.error("Failed to update goodies for event {} job {}", eventId, jobExecutionId, e);
        }

        try {
            saveImportJob(jobExecution, eventId, userId, jobExecutionId, jobStatus, writeCount, skipCount, readCount, readSkipCount);
        } catch (Exception e) {
            log.error("Failed to persist import job for job {} event {}", jobExecutionId, eventId, e);
        }

        try {
            eventLatestImportRepository.save(new EventLatestImport(eventId, jobExecutionId.toString()));
        } catch (Exception e) {
            log.error("Failed to update latest import pointer for job {} event {}", jobExecutionId, eventId, e);
        }

        if ("COMPLETED".equals(jobStatus)) {
            User uploader = userRepository.findById(userId).orElse(null);
            reconcileStats(eventId, uploader, jobExecutionId);
            notifyOrgAdminsOfImport(uploader, eventId, writeCount, skipCount);
        }

        sendImportNotification(userId, eventId, jobStatus, writeCount, skipCount);
    }

    /**
     * On a successful import, also inform the organization's admin(s). The uploader is passed as the
     * actor so an admin who ran the import themselves is excluded here and only gets their own
     * {@code USER} notification — no duplicate.
     */
    private void notifyOrgAdminsOfImport(User uploader, Long eventId, int writeCount, int skipCount) {
        if (uploader == null || uploader.getOrganization() == null) {
            return;
        }
        notificationService.notify(NotifyRequest.builder()
                .audience(NotificationAudience.ORGANIZATION_ADMINS)
                .organizationId(uploader.getOrganization().getId())
                .type(NotificationType.IMPORT_COMPLETED)
                .title("CSV Import Completed")
                .message(String.format("%s imported %d participant(s) for event #%d. %d row(s) skipped.",
                        uploader.getUsername(), writeCount, eventId, skipCount))
                .entityType("EVENT")
                .entityId(String.valueOf(eventId))
                .actor(uploader)
                .build());
    }

    private void sendImportNotification(Long userId, Long eventId, String jobStatus, int writeCount, int skipCount) {
        boolean completed = "COMPLETED".equals(jobStatus);
        String title = completed ? "CSV Import Completed" : "CSV Import Failed";
        String message = completed
                ? String.format("Successfully imported %d participant(s) for event #%d. %d row(s) skipped.",
                        writeCount, eventId, skipCount)
                : String.format("Import job for event #%d ended with status %s. %d row(s) written, %d skipped.",
                        eventId, jobStatus, writeCount, skipCount);

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

    private void reconcileStats(Long eventId, User user, Long jobExecutionId) {
        try {
            if (user == null) {
                log.warn("Skipping stats reconcile for job {} event {}: uploader user not found",
                        jobExecutionId, eventId);
                return;
            }
            eventStatsService.reconcile(eventId, user);
            log.info("Reconciled stats counters after batch import job {} for event {}", jobExecutionId, eventId);
        } catch (Exception e) {
            log.error("Failed to reconcile stats counters after batch import job {} for event {}",
                    jobExecutionId, eventId, e);
        }
    }

    private void saveImportJob(JobExecution jobExecution, Long eventId, Long userId,
                                Long jobExecutionId, String jobStatus,
                                int writeCount, int skipCount, int readCount, int readSkipCount) {
        String eventName = jobExecution.getJobParameters().getString("eventName");
        String fileName = jobExecution.getJobParameters().getString("fileName");
        String goodiesDetected = jobExecution.getExecutionContext().getString("goodiesColumns", null);

        String errorSummaryJson;
        if ("STOPPED".equals(jobStatus)) {
            errorSummaryJson = "{\"reason\":\"Stopped by user\"}";
        } else {
            // Only validationErrors is reliably tracked; other breakdown fields are unknown from Spring Batch counters
            ErrorSummary errorSummary = ErrorSummary.builder()
                    .validationErrors(skipCount)
                    .build();
            try {
                errorSummaryJson = objectMapper.writeValueAsString(errorSummary);
            } catch (Exception e) {
                log.warn("Failed to serialize error summary for job {}", jobExecutionId, e);
                errorSummaryJson = null;
            }
        }

        ImportJob.ImportStatus status = "COMPLETED".equals(jobStatus)
                ? ImportJob.ImportStatus.COMPLETED
                : ImportJob.ImportStatus.FAILED;

        String modeParam = jobExecution.getJobParameters().getString("mode");
        ImportMode mode = ImportMode.ADD_ON.name().equals(modeParam) ? ImportMode.ADD_ON : ImportMode.IMPORT;

        // Replace the IN_PROGRESS placeholder inserted at launch time. The PK changes from UUID to
        // jobExecutionId so delete-then-insert keeps downstream consumers (EventLatestImport, DDB) consistent.
        importJobRepository.findByEventIdAndStatus(eventId, ImportJob.ImportStatus.IN_PROGRESS)
                .ifPresent(importJobRepository::delete);

        ImportJob importJob = ImportJob.builder()
                .importId(jobExecutionId.toString())
                .eventId(eventId)
                .eventName(eventName != null ? eventName : "")
                .fileName(fileName != null ? fileName : "")
                .totalRows(readCount + readSkipCount)
                .successCount(writeCount)
                .failureCount(skipCount)
                .status(status)
                .mode(mode)
                .errorSummary(errorSummaryJson)
                .goodiesDetected(goodiesDetected)
                .importedBy(userId)
                .build();

        importJobRepository.save(importJob);
        log.info("Saved ImportJob {} for event {} status={}", jobExecutionId, eventId, status);
    }

    private void updateEventGoodies(Long eventId, JobExecution jobExecution) {
        String goodiesColumns = jobExecution.getExecutionContext().getString("goodiesColumns", null);
        if (goodiesColumns == null || goodiesColumns.isBlank()) return;

        Event event = eventRepository.findById(eventId).orElse(null);
        if (event == null) {
            log.warn("Event {} not found when updating goodies", eventId);
            return;
        }

        try {
            List<String> goodiesList = List.of(goodiesColumns.split(","));
            EventLimit limits = eventLimitRepository.findByEventId(eventId)
                    .orElseGet(() -> EventLimit.builder().build());
            if (goodiesList.size() > limits.getMaxGoodies()) {
                log.warn("Skipping goodies update for event {}: CSV has {} goodies columns but limit is {}",
                        eventId, goodiesList.size(), limits.getMaxGoodies());
                return;
            }
            event.setEventGoodies(objectMapper.writeValueAsString(goodiesList));
            eventRepository.save(event);
            log.info("Updated event {} goodies: {}", eventId, goodiesColumns);
        } catch (Exception e) {
            log.error("Failed to serialize goodies for event {}", eventId, e);
        }
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
            log.warn("Failed to delete temp file: {}", path, e);
        }
    }
}
