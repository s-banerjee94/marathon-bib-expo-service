package com.timekeeper.bibexpo.util;

import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.Closeable;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Streaming view over a parsed CSV. The header is consumed eagerly when the stream is opened
 * so {@link #getGoodiesColumns()} is available immediately, but data rows are pulled lazily
 * via {@link #nextRow()}. Closing this stream also closes the underlying parser and reader.
 */
public class CsvParseStream implements Closeable {

    private final CSVParser parser;
    private final Iterator<CSVRecord> iterator;
    private final Map<String, String> headerCaseMapping;
    private final List<String> goodiesColumns;
    private int rowNumber = 1;

    public CsvParseStream(CSVParser parser,
                          Map<String, String> headerCaseMapping,
                          List<String> goodiesColumns) {
        this.parser = parser;
        this.iterator = parser.iterator();
        this.headerCaseMapping = headerCaseMapping;
        this.goodiesColumns = goodiesColumns;
    }

    /**
     * @return the goodies (extra) column names detected from the header, in original order
     */
    public List<String> getGoodiesColumns() {
        return goodiesColumns;
    }

    /**
     * @return the next CSV row, or {@code null} if the stream is exhausted
     */
    public CsvRow nextRow() {
        if (!iterator.hasNext()) {
            return null;
        }
        CSVRecord record = iterator.next();
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
            goodies.put(goodieColumn, (value != null && !value.isEmpty()) ? value : "Not mentioned");
        }
        csvRow.setGoodies(goodies);

        return csvRow;
    }

    @Override
    public void close() throws IOException {
        parser.close();
    }

    private String getField(CSVRecord record, String columnName) {
        if (columnName == null) return null;
        try {
            String value = record.get(columnName);
            return value != null && !value.trim().isEmpty() ? value.trim() : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Integer parseInteger(String value) {
        if (value == null || value.isEmpty()) return null;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String normalizeEmail(String email) {
        if (email == null || email.trim().isEmpty()) return null;
        return email.trim().toLowerCase();
    }

    private String normalizeString(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        return value.trim().toUpperCase();
    }
}
