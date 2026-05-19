package com.timekeeper.bibexpo.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Slf4j
public class CsvParserUtil {

    private static final List<String> STANDARD_COLUMNS = Arrays.asList(
            "chip no", "bib no", "name", "dob(dd-mm-yyy)",
            "age", "gender", "race", "category",
            "phone", "email-id", "country", "city"
    );

    /**
     * Open a streaming view over the CSV. The header is parsed and validated eagerly;
     * data rows are read lazily as the caller pulls them via {@link CsvParseStream#nextRow()}.
     * The returned stream owns the underlying parser and must be closed by the caller.
     */
    public CsvParseStream openStream(InputStream inputStream) throws IOException {
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

        return new CsvParseStream(csvParser, headerCaseMapping, goodiesColumns);
    }

    /**
     * Eagerly parse the entire CSV into a {@link CsvParseResult}. Backed by {@link #openStream}.
     * Prefer {@link #openStream} for large files to avoid loading every row into memory.
     */
    public CsvParseResult parseCsv(InputStream inputStream) throws IOException {
        try (CsvParseStream stream = openStream(inputStream)) {
            List<CsvRow> rows = new ArrayList<>();
            CsvRow row;
            while ((row = stream.nextRow()) != null) {
                rows.add(row);
            }
            return CsvParseResult.builder()
                    .rows(rows)
                    .goodiesColumns(stream.getGoodiesColumns())
                    .totalRows(rows.size())
                    .build();
        }
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
}
