package com.timekeeper.bibexpo.batch;

import com.timekeeper.bibexpo.model.dto.request.ImportMappingRequest;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class CsvParserUtil {

    private static final char DEFAULT_DELIMITER = ',';

    /**
     * Open a streaming view over the CSV using the frontend-supplied column mapping. Columns are
     * matched by zero-based physical position, so the file header (if any) is not validated here —
     * the mapping is the source of truth and is validated by the service layer before launch.
     * The returned stream owns the underlying parser and must be closed by the caller.
     */
    public CsvParseStream openStream(InputStream inputStream, ImportMappingRequest mapping) throws IOException {
        Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);

        CSVParser csvParser = CSVFormat.DEFAULT.builder()
                .setDelimiter(resolveDelimiter(mapping.getDelimiter()))
                .setTrim(true)
                .build()
                .parse(reader);

        Map<Integer, String> fieldByIndex = new LinkedHashMap<>();
        if (mapping.getMappings() != null) {
            for (ImportMappingRequest.ColumnMapping m : mapping.getMappings()) {
                fieldByIndex.put(m.getCsvColumnIndex(), m.getTargetField());
            }
        }

        Map<Integer, String> goodiesByIndex = toIndexKeyMap(mapping.getGoodies());
        Map<Integer, String> otherByIndex = toIndexKeyMap(mapping.getOther());
        List<String> goodiesColumns = new ArrayList<>(goodiesByIndex.values());

        log.info("Opening dynamic CSV stream: {} mapped fields, {} goodies columns, {} additional columns",
                fieldByIndex.size(), goodiesByIndex.size(), otherByIndex.size());

        return new CsvParseStream(csvParser, mapping.isHasHeader(),
                fieldByIndex, goodiesByIndex, otherByIndex, goodiesColumns);
    }

    private Map<Integer, String> toIndexKeyMap(List<ImportMappingRequest.ExtraColumn> columns) {
        Map<Integer, String> map = new LinkedHashMap<>();
        if (columns != null) {
            for (ImportMappingRequest.ExtraColumn c : columns) {
                map.put(c.getCsvColumnIndex(), c.getCsvColumn());
            }
        }
        return map;
    }

    private char resolveDelimiter(String delimiter) {
        return (delimiter != null && !delimiter.isEmpty()) ? delimiter.charAt(0) : DEFAULT_DELIMITER;
    }
}
