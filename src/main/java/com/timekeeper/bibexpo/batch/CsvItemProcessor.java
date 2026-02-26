package com.timekeeper.bibexpo.batch;

import com.timekeeper.bibexpo.model.dynamodb.ParticipantDDB;
import com.timekeeper.bibexpo.util.CsvRow;
import com.timekeeper.bibexpo.validator.CsvRowValidator;
import com.timekeeper.bibexpo.validator.ValidationError;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;

@Component
@StepScope
@RequiredArgsConstructor
@Slf4j
public class CsvItemProcessor implements ItemProcessor<CsvRow, ParticipantDDB> {

    private final CsvRowValidator csvRowValidator;

    @Value("#{jobParameters['eventId']}")
    private String eventId;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    @Override
    public ParticipantDDB process(CsvRow row) {
        List<ValidationError> errors = csvRowValidator.validate(row);
        if (!errors.isEmpty()) {
            log.warn("Skipping row {} due to validation errors: {}", row.getRowNumber(), errors);
            throw new BatchValidationException("Row " + row.getRowNumber() + " invalid", errors);
        }

        String timestamp = LocalDateTime.now().format(FORMATTER);
        String raceName = row.getRaceName() != null ? row.getRaceName() : "";
        String categoryName = row.getCategoryName() != null ? row.getCategoryName() : "";

        return ParticipantDDB.builder()
                .eventId(eventId)
                .bibNumber(row.getBibNumber())
                .chipNumber(row.getChipNumber())
                .fullName(row.getFullName())
                .email(row.getEmail())
                .phoneNumber(row.getPhone())
                .dateOfBirth(row.getDateOfBirth())
                .age(row.getAge())
                .gender(row.getGender())
                .country(row.getCountry())
                .city(row.getCity())
                .raceName(raceName)
                .categoryName(categoryName)
                .goodies(row.getGoodies() != null ? new HashMap<>(row.getGoodies()) : new HashMap<>())
                .createdAt(timestamp)
                .createdBy("batch-import")
                .updatedAt(timestamp)
                .updatedBy("batch-import")
                .build();
    }
}
