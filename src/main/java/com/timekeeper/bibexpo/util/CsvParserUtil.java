package com.timekeeper.bibexpo.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Component
@Slf4j
public class CsvParserUtil {

    private static final List<String> STANDARD_COLUMNS = Arrays.asList(
            "chip no", "bib no", "name", "dob(dd-mm-yyy)",
            "age", "gender", "race", "category",
            "phone", "email-id", "country", "city"
    );

    public CsvParseResult parseCsv(InputStream inputStream) throws IOException {
        Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
        CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT
                .builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setTrim(true)
                .setAllowMissingColumnNames(true)
                .build());

        Map<String, Integer> originalHeaderMap = csvParser.getHeaderMap();

        validateEmptyHeaders(originalHeaderMap);

        Map<String, Integer> normalizedHeaderMap = new HashMap<>();
        Map<String, String> headerCaseMapping = new HashMap<>();
        for (Map.Entry<String, Integer> entry : originalHeaderMap.entrySet()) {
            String normalizedKey = entry.getKey().toLowerCase().trim();
            normalizedHeaderMap.put(normalizedKey, entry.getValue());
            headerCaseMapping.put(normalizedKey, entry.getKey());
        }

        validateCsvHeader(normalizedHeaderMap, headerCaseMapping);

        log.info("All CSV headers detected (in order): {}",
                normalizedHeaderMap.entrySet().stream()
                        .sorted(Map.Entry.comparingByValue())
                        .map(e -> String.format("'%s'", headerCaseMapping.get(e.getKey())))
                        .collect(Collectors.joining(", ")));

        List<String> goodiesColumns = normalizedHeaderMap.keySet().stream()
                .filter(header -> !STANDARD_COLUMNS.contains(header))
                .sorted(Comparator.comparing(normalizedHeaderMap::get))
                .map(headerCaseMapping::get)
                .toList();

        log.info("Detected {} goodies columns: {}", goodiesColumns.size(), goodiesColumns);

        List<CsvRow> rows = new ArrayList<>();
        int rowNumber = 1;

        for (CSVRecord record : csvParser) {
            rowNumber++;
            CsvRow csvRow = CsvRow.builder()
                    .rowNumber(rowNumber)
                    .chipNumber(getField(record, headerCaseMapping.get("chip no")))
                    .bibNumber(getField(record, headerCaseMapping.get("bib no")))
                    .fullName(getField(record, headerCaseMapping.get("name")))
                    .dateOfBirth(getField(record, headerCaseMapping.get("dob(dd-mm-yyy)")))
                    .age(parseInteger(getField(record, headerCaseMapping.get("age"))))
                    .gender(normalizeString(getField(record, headerCaseMapping.get("gender"))))
                    .raceName(getField(record, headerCaseMapping.get("race")))
                    .categoryName(getField(record, headerCaseMapping.get("category")))
                    .phone(getField(record, headerCaseMapping.get("phone")))
                    .email(normalizeEmail(getField(record, headerCaseMapping.get("email-id"))))
                    .country(getField(record, headerCaseMapping.get("country")))
                    .city(getField(record, headerCaseMapping.get("city")))
                    .build();

            Map<String, String> goodies = new HashMap<>();
            for (String goodieColumn : goodiesColumns) {
                String value = getField(record, goodieColumn);
                if (rowNumber <= 3) {
                    log.info("Row {}: Column '{}' raw value = '{}'", rowNumber, goodieColumn, value);
                }
                if (value != null && !value.isEmpty()) {
                    goodies.put(goodieColumn, value);
                } else {
                    goodies.put(goodieColumn, "Not mentioned");
                    if (rowNumber <= 3) {
                        log.info("Row {}: Column '{}' is null or empty - storing as 'Not mentioned'", rowNumber, goodieColumn);
                    }
                }
            }
            csvRow.setGoodies(goodies);

            if (rowNumber <= 5) {
                log.info("Row {}: Captured {} goodies: {}", rowNumber, goodies.size(), goodies);
            }

            rows.add(csvRow);
        }

        return CsvParseResult.builder()
                .rows(rows)
                .goodiesColumns(goodiesColumns)
                .totalRows(rows.size())
                .build();
    }

    private void validateEmptyHeaders(Map<String, Integer> originalHeaderMap) {
        List<String> headers = originalHeaderMap.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .toList();

        for (int i = 0; i < headers.size(); i++) {
            String header = headers.get(i);
            if (header == null || header.trim().isEmpty()) {
                throw new com.timekeeper.bibexpo.exception.InvalidCsvFormatException(
                        String.format("CSV header contains an empty or blank column name at position %d. " +
                                "All column headers must have names. Found headers: %s",
                                i + 1, headers));
            }
        }
    }

    private void validateCsvHeader(Map<String, Integer> normalizedHeaderMap, Map<String, String> headerCaseMapping) {
        List<String> actualNormalizedHeaders = normalizedHeaderMap.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .toList();

        if (actualNormalizedHeaders.size() < STANDARD_COLUMNS.size()) {
            List<String> originalHeaders = actualNormalizedHeaders.stream()
                    .map(headerCaseMapping::get)
                    .toList();
            throw new com.timekeeper.bibexpo.exception.InvalidCsvFormatException(
                    String.format("CSV header must contain at least %d columns. Found %d columns. " +
                            "Expected columns: %s. Found: %s",
                            STANDARD_COLUMNS.size(), actualNormalizedHeaders.size(), STANDARD_COLUMNS, originalHeaders));
        }

        for (int i = 0; i < STANDARD_COLUMNS.size(); i++) {
            String expected = STANDARD_COLUMNS.get(i);
            String actual = actualNormalizedHeaders.get(i);

            if (!expected.equals(actual)) {
                String originalActual = headerCaseMapping.get(actual);
                throw new com.timekeeper.bibexpo.exception.InvalidCsvFormatException(
                        String.format("CSV header mismatch at column %d. Expected '%s' but found '%s'. " +
                                "Required column order: %s",
                                i + 1, expected, originalActual, STANDARD_COLUMNS));
            }
        }

        log.info("CSV header validation passed. All {} standard columns matched", STANDARD_COLUMNS.size());
    }

    private String getField(CSVRecord record, String columnName) {
        try {
            String value = record.get(columnName);
            return value != null && !value.trim().isEmpty() ? value.trim() : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Integer parseInteger(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            log.warn("Failed to parse integer value: {}", value);
            return null;
        }
    }

    private String normalizeEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return null;
        }
        return email.trim().toLowerCase();
    }

    private String normalizeString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim().toUpperCase();
    }
}
