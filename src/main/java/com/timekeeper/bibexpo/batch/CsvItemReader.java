package com.timekeeper.bibexpo.batch;

import com.timekeeper.bibexpo.model.dto.request.ImportMappingRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.annotation.BeforeStep;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.ItemStreamReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.StringJoiner;

@Component
@StepScope
@RequiredArgsConstructor
@Slf4j
public class CsvItemReader implements ItemStreamReader<CsvRow> {

    private static final String CURRENT_INDEX_KEY = "csv.reader.current.index";
    private static final String DUPLICATES_KEY = "duplicateBibErrors";
    private static final String CHIP_DUPLICATES_KEY = "duplicateChipErrors";

    private final CsvParserUtil csvParserUtil;
    private final ObjectMapper objectMapper;

    @Value("#{jobParameters['filePath']}")
    private String filePath;

    @Value("#{jobParameters['mappingPath']}")
    private String mappingPath;

    private StepExecution stepExecution;
    private InputStream inputStream;
    private CsvParseStream stream;
    private Set<String> seenBibs;
    private StringJoiner duplicates;
    private Set<String> seenChips;
    private StringJoiner chipDuplicates;
    private int currentIndex;
    private int skipToIndex;

    @BeforeStep
    public void beforeStep(StepExecution stepExecution) {
        this.stepExecution = stepExecution;
    }

    @Override
    public void open(ExecutionContext executionContext) throws ItemStreamException {
        log.info("CsvItemReader: opening CSV file at {}", filePath);
        try {
            ImportMappingRequest mapping = objectMapper.readValue(new File(mappingPath), ImportMappingRequest.class);
            inputStream = new FileInputStream(filePath);
            stream = csvParserUtil.openStream(inputStream, mapping);
            seenBibs = new HashSet<>();
            duplicates = new StringJoiner(",");
            seenChips = new HashSet<>();
            chipDuplicates = new StringJoiner(",");
            currentIndex = 0;

            stepExecution.getJobExecution().getExecutionContext()
                    .put("goodiesColumns", String.join(",", stream.getGoodiesColumns()));

            skipToIndex = executionContext.containsKey(CURRENT_INDEX_KEY)
                    ? executionContext.getInt(CURRENT_INDEX_KEY) : 0;
            if (skipToIndex > 0) {
                log.info("Resuming CSV import from row index {}", skipToIndex);
            }
        } catch (Exception e) {
            closeQuietly();
            throw new ItemStreamException("Failed to open CSV file: " + filePath, e);
        }
    }

    @Override
    public CsvRow read() {
        if (stream == null) return null;

        CsvRow row;
        while ((row = stream.nextRow()) != null) {
            if (!seenBibs.add(row.getBibNumber())) {
                duplicates.add(row.getRowNumber() + ":" + row.getBibNumber());
                log.warn("Duplicate BIB {} at row {}, excluding from import",
                        row.getBibNumber(), row.getRowNumber());
                continue;
            }
            String chipNumber = row.getChipNumber();
            if (chipNumber != null && !chipNumber.isBlank() && !seenChips.add(chipNumber)) {
                chipDuplicates.add(row.getRowNumber() + ":" + chipNumber);
                log.warn("Duplicate CHIP {} at row {}, excluding from import",
                        chipNumber, row.getRowNumber());
                continue;
            }
            if (currentIndex < skipToIndex) {
                currentIndex++;
                continue;
            }
            currentIndex++;
            return row;
        }
        return null;
    }

    @Override
    public void update(ExecutionContext executionContext) throws ItemStreamException {
        executionContext.putInt(CURRENT_INDEX_KEY, currentIndex);
        if (duplicates != null) {
            stepExecution.getExecutionContext().putString(DUPLICATES_KEY, duplicates.toString());
        }
        if (chipDuplicates != null) {
            stepExecution.getExecutionContext().putString(CHIP_DUPLICATES_KEY, chipDuplicates.toString());
        }
    }

    @Override
    public void close() throws ItemStreamException {
        closeQuietly();
    }

    private void closeQuietly() {
        if (stream != null) {
            try { stream.close(); } catch (IOException e) {
                log.warn("Failed to close CSV parser", e);
            }
            stream = null;
        }
        if (inputStream != null) {
            try { inputStream.close(); } catch (IOException e) {
                log.warn("Failed to close CSV input stream", e);
            }
            inputStream = null;
        }
        seenBibs = null;
        seenChips = null;
    }
}
