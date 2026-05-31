package com.timekeeper.bibexpo.service.impl;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.timekeeper.bibexpo.exception.EventNotFoundException;
import com.timekeeper.bibexpo.exception.EventDisabledException;
import com.timekeeper.bibexpo.exception.CsvImportException;
import com.timekeeper.bibexpo.exception.ImportAlreadyRunningException;
import com.timekeeper.bibexpo.exception.ImportNotRunningException;
import com.timekeeper.bibexpo.exception.InvalidCsvFormatException;
import com.timekeeper.bibexpo.annotation.Auditable;
import com.timekeeper.bibexpo.aspect.AuditContextHolder;
import com.timekeeper.bibexpo.model.dto.request.ImportMappingRequest;
import com.timekeeper.bibexpo.model.enums.AuditAction;
import com.timekeeper.bibexpo.model.enums.AuditEntityType;
import com.timekeeper.bibexpo.model.enums.ParticipantImportField;
import com.timekeeper.bibexpo.model.entity.EventStatus;
import com.timekeeper.bibexpo.model.dto.response.BatchImportResponse;
import com.timekeeper.bibexpo.model.dto.response.BatchJobStatusResponse;
import com.timekeeper.bibexpo.model.dto.response.ImportError;
import com.timekeeper.bibexpo.model.dto.response.ImportErrorListResponse;
import com.timekeeper.bibexpo.model.dto.response.ImportFieldResponse;
import com.timekeeper.bibexpo.model.dynamodb.ImportErrorDDB;
import com.timekeeper.bibexpo.model.entity.Event;
import com.timekeeper.bibexpo.model.entity.ImportJob;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.repository.EventLatestImportRepository;
import com.timekeeper.bibexpo.repository.EventRepository;
import com.timekeeper.bibexpo.repository.ImportJobRepository;
import com.timekeeper.bibexpo.repository.dynamodb.ImportErrorDDBRepository;
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
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BatchImportServiceImpl implements BatchImportService {

    private final JobLauncher asyncJobLauncher;
    private final JobOperator jobOperator;
    private final Job csvImportJob;
    private final JobExplorer jobExplorer;
    private final ImportErrorDDBRepository importErrorDDBRepository;
    private final EventRepository eventRepository;
    private final EventAccessValidator eventAccessValidator;
    private final ObjectMapper objectMapper;
    private final EventLatestImportRepository eventLatestImportRepository;
    private final ImportJobRepository importJobRepository;

    @Override
    @Auditable(entityType = AuditEntityType.PARTICIPANT, action = AuditAction.IMPORT)
    public BatchImportResponse launchImport(Long eventId, MultipartFile file, String mappingJson, User currentUser) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException());

        eventAccessValidator.validateUserAuthorizationForEvent(currentUser, event);

        EventStatus eventStatus = event.getStatus();
        if (eventStatus == EventStatus.PUBLISHED || eventStatus == EventStatus.COMPLETED || eventStatus == EventStatus.CANCELLED) {
            throw new EventDisabledException("Importing participants is not allowed once the event is published, completed, or cancelled.");
        }

        if (importJobRepository.existsByEventIdAndStatus(eventId, ImportJob.ImportStatus.IN_PROGRESS)) {
            throw new ImportAlreadyRunningException("An import is already running for this event.");
        }

        ImportMappingRequest mapping = parseAndValidateMapping(mappingJson);

        Path tempFile;
        Path mappingFile;
        try {
            tempFile = Files.createTempFile("csv-import-", ".csv");
            file.transferTo(tempFile);
            mappingFile = Files.createTempFile("csv-mapping-", ".json");
            Files.writeString(mappingFile, objectMapper.writeValueAsString(mapping));
        } catch (IOException e) {
            throw new CsvImportException("Failed to save the uploaded file. Please try again.", e);
        }

        String originalFileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown";
        ImportJob pendingJob = ImportJob.builder()
                .importId(UUID.randomUUID().toString())
                .eventId(eventId)
                .eventName(event.getEventName())
                .fileName(originalFileName)
                .totalRows(0)
                .successCount(0)
                .failureCount(0)
                .status(ImportJob.ImportStatus.IN_PROGRESS)
                .importedBy(currentUser.getId())
                .build();
        importJobRepository.save(pendingJob);

        JobParameters params = new JobParametersBuilder()
                .addString("eventId", eventId.toString())
                .addString("eventName", event.getEventName())
                .addString("filePath", tempFile.toString())
                .addString("mappingPath", mappingFile.toString())
                .addString("fileName", originalFileName)
                .addString("uploadedByUserId", currentUser.getId().toString())
                .addLong("run", System.currentTimeMillis())
                .toJobParameters();

        try {
            JobExecution execution = asyncJobLauncher.run(csvImportJob, params);
            pendingJob.setJobExecutionId(execution.getId());
            importJobRepository.save(pendingJob);
            log.info("Launched batch import job {} for event {}", execution.getId(), eventId);
            AuditContextHolder.setEntityLabel(event.getEventName());
            if (event.getOrganization() != null) {
                AuditContextHolder.setOrganizationId(event.getOrganization().getId());
            }
            return new BatchImportResponse(execution.getId(), execution.getStatus().toString());
        } catch (Exception e) {
            pendingJob.setStatus(ImportJob.ImportStatus.FAILED);
            pendingJob.setErrorSummary("{\"reason\":\"Failed to launch the import job\"}");
            importJobRepository.save(pendingJob);
            throw new CsvImportException("Failed to start the import. Please try again.", e);
        }
    }

    private ImportMappingRequest parseAndValidateMapping(String mappingJson) {
        if (mappingJson == null || mappingJson.isBlank()) {
            throw new InvalidCsvFormatException("Please provide the column mapping for the import.");
        }

        ImportMappingRequest mapping;
        try {
            mapping = objectMapper.readValue(mappingJson, ImportMappingRequest.class);
        } catch (Exception e) {
            throw new InvalidCsvFormatException("The column mapping could not be read. Please try again.");
        }

        List<ImportMappingRequest.ColumnMapping> mappings = mapping.getMappings();
        if (mappings == null || mappings.isEmpty()) {
            throw new InvalidCsvFormatException("Please map at least one column before importing.");
        }

        Set<String> seenTargets = new HashSet<>();
        Set<Integer> usedIndexes = new HashSet<>();
        for (ImportMappingRequest.ColumnMapping m : mappings) {
            if (m.getCsvColumnIndex() == null || m.getCsvColumnIndex() < 0) {
                throw new InvalidCsvFormatException("A mapped column is missing its position.");
            }
            if (!ParticipantImportField.isKnown(m.getTargetField())) {
                throw new InvalidCsvFormatException("One of the selected fields is not recognized.");
            }
            if (!seenTargets.add(m.getTargetField())) {
                throw new InvalidCsvFormatException("The same field has been mapped to more than one column.");
            }
            if (!usedIndexes.add(m.getCsvColumnIndex())) {
                throw new InvalidCsvFormatException("A column has been mapped more than once.");
            }
        }

        List<String> missingRequired = new ArrayList<>();
        for (ParticipantImportField field : ParticipantImportField.requiredFields()) {
            if (!seenTargets.contains(field.getKey())) {
                missingRequired.add(field.getLabel());
            }
        }
        if (!missingRequired.isEmpty()) {
            String noun = missingRequired.size() == 1 ? "column" : "columns";
            throw new InvalidCsvFormatException(
                    "Please map the " + joinReadable(missingRequired) + " " + noun + " before importing.");
        }

        validateExtraColumns(mapping.getGoodies(), usedIndexes);
        validateExtraColumns(mapping.getOther(), usedIndexes);

        return mapping;
    }

    private String joinReadable(List<String> items) {
        if (items.size() == 1) return items.get(0);
        if (items.size() == 2) return items.get(0) + " and " + items.get(1);
        return String.join(", ", items.subList(0, items.size() - 1))
                + ", and " + items.get(items.size() - 1);
    }

    private void validateExtraColumns(List<ImportMappingRequest.ExtraColumn> columns, Set<Integer> usedIndexes) {
        if (columns == null) return;
        for (ImportMappingRequest.ExtraColumn c : columns) {
            if (c.getCsvColumnIndex() == null || c.getCsvColumnIndex() < 0) {
                throw new InvalidCsvFormatException("A retained column is missing its position.");
            }
            if (c.getCsvColumn() == null || c.getCsvColumn().isBlank()) {
                throw new InvalidCsvFormatException("A retained column is missing its name.");
            }
            if (!usedIndexes.add(c.getCsvColumnIndex())) {
                throw new InvalidCsvFormatException("A column has been mapped more than once.");
            }
        }
    }

    @Override
    public BatchJobStatusResponse stopImport(Long eventId, Long jobExecutionId, User currentUser) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(EventNotFoundException::new);
        eventAccessValidator.validateUserAuthorizationForEvent(currentUser, event);

        ImportJob job = importJobRepository.findByJobExecutionIdAndEventId(jobExecutionId, eventId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "This import job does not belong to the selected event."));

        if (job.getStatus() != ImportJob.ImportStatus.IN_PROGRESS) {
            throw new ImportNotRunningException("This import is not currently running.");
        }

        try {
            boolean stopped = jobOperator.stop(jobExecutionId);
            log.info("Stop requested for jobExecutionId={} by user {} (signalled={})",
                    jobExecutionId, currentUser.getId(), stopped);
        } catch (Exception e) {
            log.warn("Failed to signal stop for jobExecutionId={}: {}", jobExecutionId, e.getMessage());
        }

        return getJobStatus(eventId, jobExecutionId);
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
            throw new EventNotFoundException();
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

    @Override
    public List<ImportFieldResponse> getImportFields() {
        return Arrays.stream(ParticipantImportField.values())
                .map(f -> ImportFieldResponse.builder()
                        .key(f.getKey())
                        .label(f.getLabel())
                        .required(f.isRequired())
                        .build())
                .toList();
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
