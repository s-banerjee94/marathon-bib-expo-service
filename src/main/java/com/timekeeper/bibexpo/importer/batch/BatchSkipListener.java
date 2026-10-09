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

    @Value("#{jobParameters['eventId']}")
    private String eventId;

    private final List<ImportRowError> collectedErrors = new ArrayList<>();

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
                }
                message = validationErrors.stream().map(ValidationError::getMessage).collect(Collectors.joining(" "));
            } else {
                message = bve.getMessage() != null ? bve.getMessage() : "This row could not be imported.";
            }
        } else {
            message = "This row could not be imported.";
        }

        collectedErrors.add(newError(item.getRowNumber(), errorType, field, message));
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        collectDuplicates(stepExecution.getExecutionContext().getString("duplicateBibErrors", ""),
                BatchValidationException.TYPE_DUPLICATE_BIB, "bibNumber", "Bib number");
        collectDuplicates(stepExecution.getExecutionContext().getString("duplicateChipErrors", ""),
                BatchValidationException.TYPE_DUPLICATE_CHIP, "chipNumber", "Chip number");

        if (!collectedErrors.isEmpty()) {
            // Sorted before insert so the auto-increment id ascends with the row number; the read
            // side can then paginate on the id alone and still return rows in file order.
            collectedErrors.sort(Comparator.comparing(ImportRowError::getRowNumber,
                    Comparator.nullsLast(Comparator.naturalOrder())));
            importRowErrorRepository.saveAll(collectedErrors);
            log.atInfo()
                    .addKeyValue("event", "import.errors_recorded")
                    .addKeyValue("eventId", eventId)
                    .addKeyValue("importId", importId)
                    .addKeyValue("count", collectedErrors.size())
                    .log("Import row errors recorded");
        }
        return stepExecution.getExitStatus();
    }

    // Entries are "row:value", comma separated, as the reader records them.
    private void collectDuplicates(String entries, String errorType, String field, String label) {
        if (entries.isBlank()) return;
        for (String entry : entries.split(",")) {
            String[] parts = entry.split(":", 2);
            if (parts.length == 2) {
                collectedErrors.add(newError(parseRowNumber(parts[0]), errorType, field,
                        label + " " + parts[1] + " appears earlier in the file, so this row was skipped."));
            }
        }
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
