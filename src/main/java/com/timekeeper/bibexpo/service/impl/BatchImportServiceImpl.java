package com.timekeeper.bibexpo.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.timekeeper.bibexpo.exception.EventNotFoundException;
import com.timekeeper.bibexpo.exception.CsvImportException;
import com.timekeeper.bibexpo.exception.ImportAlreadyRunningException;
import com.timekeeper.bibexpo.model.dto.response.BatchImportResponse;
import com.timekeeper.bibexpo.model.dto.response.BatchJobStatusResponse;
import com.timekeeper.bibexpo.model.dto.response.ImportError;
import com.timekeeper.bibexpo.model.dto.response.ImportErrorListResponse;
import com.timekeeper.bibexpo.model.dynamodb.ImportErrorDDB;
import com.timekeeper.bibexpo.model.entity.Event;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.repository.EventLatestImportRepository;
import com.timekeeper.bibexpo.repository.EventRepository;
import com.timekeeper.bibexpo.repository.dynamodb.ImportErrorDDBRepository;
import com.timekeeper.bibexpo.repository.dynamodb.ParticipantDDBRepository;
import com.timekeeper.bibexpo.service.BatchImportService;
import com.timekeeper.bibexpo.service.validator.EventAccessValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class BatchImportServiceImpl implements BatchImportService {

    private final JobLauncher asyncJobLauncher;
    private final Job csvImportJob;
    private final JobExplorer jobExplorer;
    private final ParticipantDDBRepository participantDDBRepository;
    private final ImportErrorDDBRepository importErrorDDBRepository;
    private final EventRepository eventRepository;
    private final EventAccessValidator eventAccessValidator;
    private final ObjectMapper objectMapper;
    private final EventLatestImportRepository eventLatestImportRepository;

    @Override
    public BatchImportResponse launchImport(Long eventId, MultipartFile file, User currentUser) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException("Event not found with ID: " + eventId));

        eventAccessValidator.validateUserAuthorizationForEvent(currentUser, event);

        boolean alreadyRunning = jobExplorer.findRunningJobExecutions("csvImportJob").stream()
                .anyMatch(e -> eventId.toString().equals(e.getJobParameters().getString("eventId")));
        if (alreadyRunning) {
            throw new ImportAlreadyRunningException("An import is already running for this event.");
        }

        Path tempFile;
        try {
            tempFile = Files.createTempFile("csv-import-", ".csv");
            file.transferTo(tempFile);
        } catch (IOException e) {
            throw new CsvImportException("Failed to save the uploaded file. Please try again.", e);
        }

        int deletedCount = participantDDBRepository.deleteAllByEventId(eventId.toString());
        log.info("Deleted {} existing participants for event {} before batch import", deletedCount, eventId);

        JobParameters params = new JobParametersBuilder()
                .addString("eventId", eventId.toString())
                .addString("eventName", event.getEventName())
                .addString("filePath", tempFile.toString())
                .addString("fileName", file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown")
                .addString("uploadedByUserId", currentUser.getId().toString())
                .addLong("run", System.currentTimeMillis())
                .toJobParameters();

        try {
            JobExecution execution = asyncJobLauncher.run(csvImportJob, params);
            log.info("Launched batch import job {} for event {}", execution.getId(), eventId);
            return new BatchImportResponse(execution.getId(), execution.getStatus().toString(), deletedCount);
        } catch (Exception e) {
            throw new CsvImportException("Failed to start the import. Please try again.", e);
        }
    }

    @Override
    public BatchJobStatusResponse getJobStatus(Long eventId, Long jobExecutionId) {
        JobExecution execution = jobExplorer.getJobExecution(jobExecutionId);
        if (execution == null) {
            throw new IllegalArgumentException("Import job not found.");
        }

        String jobEventId = execution.getJobParameters().getString("eventId");
        if (!eventId.toString().equals(jobEventId)) {
            throw new IllegalArgumentException("This import job does not belong to the selected event.");
        }

        BatchJobStatusResponse response = new BatchJobStatusResponse();
        response.setJobExecutionId(jobExecutionId);
        response.setStatus(execution.getStatus().toString());
        response.setStartTime(execution.getStartTime());
        response.setEndTime(execution.getEndTime());

        execution.getStepExecutions().stream().findFirst().ifPresent(step -> {
            response.setReadCount(step.getReadCount());
            response.setWriteCount(step.getWriteCount());
            response.setSkipCount(step.getSkipCount());
        });

        return response;
    }

    @Override
    public ImportErrorListResponse getLatestBatchImportErrors(Long eventId, int limit, String lastEvaluatedKey) {
        if (!eventRepository.existsById(eventId)) {
            throw new EventNotFoundException("Event not found with ID: " + eventId);
        }
        return eventLatestImportRepository.findById(eventId)
                .map(latest -> {
                    String importId = latest.getImportId();
                    Map<String, AttributeValue> exclusiveStartKey = decodeLastKey(lastEvaluatedKey);
                    Page<ImportErrorDDB> page = importErrorDDBRepository.findByImportId(importId, limit, exclusiveStartKey);

                    if (page == null) {
                        return ImportErrorListResponse.builder()
                                .importId(importId)
                                .errors(List.of())
                                .count(0)
                                .hasMore(false)
                                .build();
                    }

                    List<ImportError> errors = page.items().stream()
                            .map(e -> ImportError.builder()
                                    .rowNumber(e.getRowNumber())
                                    .errorType(e.getErrorType())
                                    .field(e.getField())
                                    .message(e.getMessage())
                                    .build())
                            .toList();

                    String nextToken = encodeLastKey(page.lastEvaluatedKey());

                    return ImportErrorListResponse.builder()
                            .importId(importId)
                            .errors(errors)
                            .count(errors.size())
                            .lastEvaluatedKey(nextToken)
                            .hasMore(nextToken != null)
                            .build();
                })
                .orElse(ImportErrorListResponse.builder()
                        .errors(List.of())
                        .count(0)
                        .hasMore(false)
                        .build());
    }

    private String encodeLastKey(Map<String, AttributeValue> lastKey) {
        if (lastKey == null || lastKey.isEmpty()) return null;
        try {
            Map<String, String> simple = new HashMap<>();
            if (lastKey.containsKey("importId")) simple.put("importId", lastKey.get("importId").s());
            if (lastKey.containsKey("rowNumber")) simple.put("rowNumber", lastKey.get("rowNumber").n());
            String json = objectMapper.writeValueAsString(simple);
            return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("Failed to encode pagination key", e);
            return null;
        }
    }

    private Map<String, AttributeValue> decodeLastKey(String token) {
        if (token == null || token.isBlank()) return Map.of();
        try {
            String json = new String(Base64.getDecoder().decode(token), StandardCharsets.UTF_8);
            Map<String, String> simple = objectMapper.readValue(json, new TypeReference<>() {});
            Map<String, AttributeValue> key = new HashMap<>();
            if (simple.containsKey("importId"))
                key.put("importId", AttributeValue.builder().s(simple.get("importId")).build());
            if (simple.containsKey("rowNumber"))
                key.put("rowNumber", AttributeValue.builder().n(simple.get("rowNumber")).build());
            return key;
        } catch (Exception e) {
            log.warn("Failed to decode pagination key, ignoring", e);
            return Map.of();
        }
    }
}
