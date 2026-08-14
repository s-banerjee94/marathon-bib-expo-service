package com.timekeeper.bibexpo.importer.batch;

import com.timekeeper.bibexpo.importer.model.entity.ImportRowError;
import com.timekeeper.bibexpo.importer.repository.ImportJobRepository;
import com.timekeeper.bibexpo.importer.repository.ImportRowErrorRepository;
import com.timekeeper.bibexpo.participant.model.dynamodb.ParticipantDDB;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.SkipListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Component
@StepScope
@RequiredArgsConstructor
@Slf4j
public class BatchSkipListener implements SkipListener<CsvRow, ParticipantDDB>, StepExecutionListener {

    private final ImportRowErrorRepository importRowErrorRepository;
    private final ImportJobRepository importJobRepository;

    // The import's own id, stable for the life of the job. Not the Spring Batch execution id: that
    // is a MySQL sequence which restarts after a reset, and errors filed under it then collided
    // with rows left behind by an earlier import that happened to hold the same number.
    @Value("#{jobParameters['importId']}")
    private String importId;

    private final List<ImportRowError> collectedErrors = new ArrayList<>();

    @Override
    public void onSkipInWrite(ParticipantDDB item, Throwable t) {
        collectedErrors.add(newError(null, "WRITE_ERROR", null,
                t.getMessage() != null ? t.getMessage() : "Write failed"));
        log.warn("Recorded write skip for participant {}: {}", item.getBibNumber(), t.getMessage());
    }

    @Override
    public void onSkipInProcess(CsvRow item, Throwable t) {
        String field = null;
        String message;
        String errorType = BatchValidationException.TYPE_VALIDATION;

        if (t instanceof BatchValidationException bve) {
            errorType = bve.getErrorType();
            List<ValidationError> validationErrors = bve.getValidationErrors();
            if (validationErrors != null && !validationErrors.isEmpty()) {
                if (validationErrors.size() == 1) {
                    field = validationErrors.get(0).getField();
                    message = validationErrors.get(0).getMessage();
                } else {
                    message = validationErrors.stream()
                            .map(e -> e.getField() + ": " + e.getMessage())
                            .collect(Collectors.joining("; "));
                }
            } else {
                message = bve.getMessage() != null ? bve.getMessage() : "Processing failed";
            }
        } else {
            message = t.getMessage() != null ? t.getMessage() : "Validation failed";
        }

        collectedErrors.add(newError(item.getRowNumber(), errorType, field, message));
        log.debug("Recorded skip for row {}: type={}, field={}, message={}",
                item.getRowNumber(), errorType, field, message);
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        String duplicateBibErrors = stepExecution.getExecutionContext().getString("duplicateBibErrors", "");
        if (!duplicateBibErrors.isBlank()) {
            for (String entry : duplicateBibErrors.split(",")) {
                String[] parts = entry.split(":", 2);
                if (parts.length == 2) {
                    Integer rowNumber = parseRowNumber(parts[0]);
                    String bibNumber = parts[1];
                    collectedErrors.add(newError(rowNumber, "DUPLICATE_BIB", "bibNumber",
                            "Duplicate BIB number '" + bibNumber + "' at row " + rowNumber));
                }
            }
        }

        String duplicateChipErrors = stepExecution.getExecutionContext().getString("duplicateChipErrors", "");
        if (!duplicateChipErrors.isBlank()) {
            for (String entry : duplicateChipErrors.split(",")) {
                String[] parts = entry.split(":", 2);
                if (parts.length == 2) {
                    Integer rowNumber = parseRowNumber(parts[0]);
                    String chipNumber = parts[1];
                    collectedErrors.add(newError(rowNumber, "DUPLICATE_CHIP", "chipNumber",
                            "Duplicate CHIP number '" + chipNumber + "' at row " + rowNumber));
                }
            }
        }

        if (!collectedErrors.isEmpty()) {
            // Sorted before insert so the auto-increment id ascends with the row number; the read
            // side can then paginate on the id alone and still return rows in file order.
            collectedErrors.sort(Comparator.comparing(ImportRowError::getRowNumber,
                    Comparator.nullsLast(Comparator.naturalOrder())));
            log.info("Writing {} import errors for import {}", collectedErrors.size(), importId);
            importRowErrorRepository.saveAll(collectedErrors);
        }
        return stepExecution.getExitStatus();
    }

    private ImportRowError newError(Integer rowNumber, String errorType, String field, String message) {
        return ImportRowError.builder()
                .importJob(importJobRepository.getReferenceById(importId))
                .rowNumber(rowNumber)
                .errorType(errorType)
                .field(field)
                .message(message)
                .createdAt(Instant.now())
                .build();
    }

    private Integer parseRowNumber(String s) {
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return null; }
    }
}
