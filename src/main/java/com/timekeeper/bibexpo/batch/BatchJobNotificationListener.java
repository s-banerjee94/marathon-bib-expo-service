package com.timekeeper.bibexpo.batch;

import tools.jackson.databind.ObjectMapper;
import com.timekeeper.bibexpo.model.dto.response.ErrorSummary;
import com.timekeeper.bibexpo.model.dto.response.NotificationResponse;
import com.timekeeper.bibexpo.model.entity.Event;
import com.timekeeper.bibexpo.model.entity.EventLatestImport;
import com.timekeeper.bibexpo.model.entity.ImportJob;
import com.timekeeper.bibexpo.model.entity.Notification;
import com.timekeeper.bibexpo.repository.EventLatestImportRepository;
import com.timekeeper.bibexpo.repository.EventRepository;
import com.timekeeper.bibexpo.repository.ImportJobRepository;
import com.timekeeper.bibexpo.repository.dynamodb.ParticipantDDBRepository;
import com.timekeeper.bibexpo.service.NotificationService;
import com.timekeeper.bibexpo.service.SseEmitterRegistry;
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
    private final SseEmitterRegistry sseEmitterRegistry;
    private final ImportJobRepository importJobRepository;
    private final EventLatestImportRepository eventLatestImportRepository;
    private final ParticipantDDBRepository participantDDBRepository;
    private final EventRepository eventRepository;
    private final ObjectMapper objectMapper;

    @Override
    public void beforeJob(JobExecution jobExecution) {
        String eventIdParam = jobExecution.getJobParameters().getString("eventId");
        if (eventIdParam == null) return;

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

        try {
            Notification notification = notificationService.createJobNotification(
                    userId, eventId, jobExecutionId, writeCount, skipCount, jobStatus);

            NotificationResponse payload = NotificationResponse.builder()
                    .id(notification.getId())
                    .title(notification.getTitle())
                    .message(notification.getMessage())
                    .read(notification.getRead())
                    .eventId(notification.getEventId())
                    .jobExecutionId(notification.getJobExecutionId())
                    .createdAt(notification.getCreatedAt())
                    .build();

            String eventName = "COMPLETED".equals(jobStatus) ? "import:completed" : "import:failed";
            sseEmitterRegistry.send(userId, eventName, payload);
        } catch (Exception e) {
            log.error("Failed to create or send notification for job {} user {}", jobExecutionId, userId, e);
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
            event.setEventGoodies(objectMapper.writeValueAsString(goodiesList));
            eventRepository.save(event);
            log.info("Updated event {} goodies: {}", eventId, goodiesColumns);
        } catch (Exception e) {
            log.error("Failed to serialize goodies for event {}", eventId, e);
        }
    }

    private void deleteTempFile(JobExecution jobExecution) {
        String filePath = jobExecution.getJobParameters().getString("filePath");
        if (filePath == null) return;
        try {
            Files.deleteIfExists(Paths.get(filePath));
        } catch (IOException e) {
            log.warn("Failed to delete temp CSV file: {}", filePath, e);
        }
    }
}
