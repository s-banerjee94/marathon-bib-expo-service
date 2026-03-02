package com.timekeeper.bibexpo.batch;

import com.timekeeper.bibexpo.model.dynamodb.ImportErrorDDB;
import com.timekeeper.bibexpo.model.dynamodb.ParticipantDDB;
import com.timekeeper.bibexpo.repository.dynamodb.ImportErrorDDBRepository;
import com.timekeeper.bibexpo.util.CsvRow;
import com.timekeeper.bibexpo.validator.ValidationError;
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

        if (t instanceof BatchValidationException bve && !bve.getValidationErrors().isEmpty()) {
            List<ValidationError> validationErrors = bve.getValidationErrors();
            if (validationErrors.size() == 1) {
                field = validationErrors.get(0).getField();
                message = validationErrors.get(0).getMessage();
            } else {
                message = validationErrors.stream()
                        .map(e -> e.getField() + ": " + e.getMessage())
                        .collect(Collectors.joining("; "));
            }
        } else {
            message = t.getMessage() != null ? t.getMessage() : "Validation failed";
        }

        collectedErrors.add(ImportErrorDDB.create(
                jobExecutionId.toString(),
                item.getRowNumber(),
                "VALIDATION_ERROR",
                field,
                message,
                ERROR_TTL_DAYS
        ));
        log.debug("Recorded skip for row {}: field={}, message={}", item.getRowNumber(), field, message);
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        if (!collectedErrors.isEmpty()) {
            log.info("Writing {} validation errors to DynamoDB for job {}", collectedErrors.size(), jobExecutionId);
            importErrorDDBRepository.saveAll(collectedErrors);
        }
        return stepExecution.getExitStatus();
    }
}
