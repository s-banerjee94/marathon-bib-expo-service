package com.timekeeper.bibexpo.batch;

import com.timekeeper.bibexpo.model.dynamodb.ImportErrorDDB;
import com.timekeeper.bibexpo.model.dynamodb.ParticipantDDB;
import com.timekeeper.bibexpo.repository.dynamodb.ImportErrorDDBRepository;
import com.timekeeper.bibexpo.util.CsvRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.SkipListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
@StepScope
@RequiredArgsConstructor
@Slf4j
public class BatchSkipListener implements SkipListener<CsvRow, ParticipantDDB>, StepExecutionListener {

    private final ImportErrorDDBRepository importErrorDDBRepository;

    @Value("#{stepExecution.jobExecution.id}")
    private Long jobExecutionId;

    private final List<ImportErrorDDB> collectedErrors = new ArrayList<>();

    private static final int ERROR_TTL_DAYS = 30;

    @Override
    public void onSkipInWrite(ParticipantDDB item, Throwable t) {
        collectedErrors.add(ImportErrorDDB.create(
                jobExecutionId.toString(),
                null,
                "WRITE_ERROR",
                null,
                t.getMessage() != null ? t.getMessage() : "Write failed",
                ERROR_TTL_DAYS
        ));
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

        collectedErrors.add(ImportErrorDDB.create(
                jobExecutionId.toString(),
                item.getRowNumber(),
                errorType,
                field,
                message,
                ERROR_TTL_DAYS
        ));
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
                    collectedErrors.add(ImportErrorDDB.create(
                            jobExecutionId.toString(),
                            rowNumber,
                            "DUPLICATE_BIB",
                            "bibNumber",
                            "Duplicate BIB number '" + bibNumber + "' at row " + rowNumber,
                            ERROR_TTL_DAYS
                    ));
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
                    collectedErrors.add(ImportErrorDDB.create(
                            jobExecutionId.toString(),
                            rowNumber,
                            "DUPLICATE_CHIP",
                            "chipNumber",
                            "Duplicate CHIP number '" + chipNumber + "' at row " + rowNumber,
                            ERROR_TTL_DAYS
                    ));
                }
            }
        }

        if (!collectedErrors.isEmpty()) {
            log.info("Writing {} errors to DynamoDB for job {}", collectedErrors.size(), jobExecutionId);
            importErrorDDBRepository.saveAll(collectedErrors);
        }
        return stepExecution.getExitStatus();
    }

    private Integer parseRowNumber(String s) {
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return null; }
    }
}
