package com.timekeeper.bibexpo.batch;

import com.timekeeper.bibexpo.util.TextUtils;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.Closeable;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Streaming view over a parsed CSV driven by a frontend-supplied column mapping. Columns are read
 * by their zero-based physical position, so duplicate, blank, or renamed headers are irrelevant.
 * Known fields are populated by target-field key; goodies and additional columns are collected into
 * their own maps keyed by the original header. Data rows are pulled lazily via {@link #nextRow()}.
 * Closing this stream also closes the underlying parser and reader.
 */
public class CsvParseStream implements Closeable {

    private static final String GOODIE_NOT_MENTIONED = "Not mentioned";

    private final CSVParser parser;
    private final Iterator<CSVRecord> iterator;
    private final boolean hasHeader;
    private final Map<Integer, String> fieldByIndex;
    private final Map<Integer, String> goodiesByIndex;
    private final Map<Integer, String> otherByIndex;
    private final List<String> goodiesColumns;

    private boolean headerSkipped;

    public CsvParseStream(CSVParser parser,
                          boolean hasHeader,
                          Map<Integer, String> fieldByIndex,
                          Map<Integer, String> goodiesByIndex,
                          Map<Integer, String> otherByIndex,
                          List<String> goodiesColumns) {
        this.parser = parser;
        this.iterator = parser.iterator();
        this.hasHeader = hasHeader;
        this.fieldByIndex = fieldByIndex;
        this.goodiesByIndex = goodiesByIndex;
        this.otherByIndex = otherByIndex;
        this.goodiesColumns = goodiesColumns;
    }

    /**
     * @return the goodies column headers from the mapping, in declared order
     */
    public List<String> getGoodiesColumns() {
        return goodiesColumns;
    }

    /**
     * @return the next CSV row, or {@code null} if the stream is exhausted
     */
    public CsvRow nextRow() {
        while (iterator.hasNext()) {
            CSVRecord record = iterator.next();
            if (hasHeader && !headerSkipped) {
                headerSkipped = true;
                continue;
            }
            return buildRow(record);
        }
        return null;
    }

    private CsvRow buildRow(CSVRecord record) {
        CsvRow row = new CsvRow();
        // Physical file line (as seen in Excel), not the record ordinal — the parser skips
        // blank lines, so a hand-rolled per-record counter drifts below the real line number.
        row.setRowNumber((int) parser.getCurrentLineNumber());

        for (Map.Entry<Integer, String> entry : fieldByIndex.entrySet()) {
            applyField(row, entry.getValue(), getField(record, entry.getKey()));
        }

        Map<String, String> goodies = new HashMap<>();
        for (Map.Entry<Integer, String> entry : goodiesByIndex.entrySet()) {
            String value = getField(record, entry.getKey());
            goodies.put(entry.getValue(), value != null ? value : GOODIE_NOT_MENTIONED);
        }
        row.setGoodies(goodies);

        Map<String, String> additionalFields = new HashMap<>();
        for (Map.Entry<Integer, String> entry : otherByIndex.entrySet()) {
            String value = getField(record, entry.getKey());
            if (value != null) {
                additionalFields.put(entry.getValue(), value);
            }
        }
        row.setAdditionalFields(additionalFields);

        return row;
    }

    private void applyField(CsvRow row, String targetField, String value) {
        switch (targetField) {
            case "chipNumber" -> row.setChipNumber(value);
            case "bibNumber" -> row.setBibNumber(value);
            case "fullName" -> row.setFullName(TextUtils.toUpperOrNull(value));
            case "dateOfBirth" -> row.setDateOfBirth(value);
            case "age" -> row.setAge(parseInteger(value));
            case "gender" -> row.setGender(TextUtils.toUpperOrNull(value));
            case "raceName" -> row.setRaceName(value);
            case "categoryName" -> row.setCategoryName(value);
            case "phoneNumber" -> row.setPhone(value);
            case "email" -> row.setEmail(TextUtils.toLowerOrNull(value));
            case "country" -> row.setCountry(value);
            case "city" -> row.setCity(value);
            default -> { /* unknown target fields are rejected before the job starts */ }
        }
    }

    @Override
    public void close() throws IOException {
        parser.close();
    }

    private String getField(CSVRecord record, Integer index) {
        if (index == null || index < 0 || index >= record.size()) return null;
        String value = record.get(index);
        return value != null && !value.trim().isEmpty() ? value.trim() : null;
    }

    private Integer parseInteger(String value) {
        if (value == null || value.isEmpty()) return null;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
