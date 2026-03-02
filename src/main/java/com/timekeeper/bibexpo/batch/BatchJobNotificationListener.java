package com.timekeeper.bibexpo.batch;

import com.timekeeper.bibexpo.model.dto.response.NotificationResponse;
import com.timekeeper.bibexpo.model.entity.Notification;
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

@Component
@RequiredArgsConstructor
@Slf4j
public class BatchJobNotificationListener implements JobExecutionListener {

    private final NotificationService notificationService;
    private final SseEmitterRegistry sseEmitterRegistry;

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
        if (!jobExecution.getStepExecutions().isEmpty()) {
            var step = jobExecution.getStepExecutions().iterator().next();
            writeCount = (int) step.getWriteCount();
            skipCount = (int) step.getSkipCount();
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
