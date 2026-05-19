package com.timekeeper.bibexpo.batch;

import com.timekeeper.bibexpo.util.CsvParseResult;
import com.timekeeper.bibexpo.util.CsvParserUtil;
import com.timekeeper.bibexpo.util.CsvRow;
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

import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;

@Component
@StepScope
@RequiredArgsConstructor
@Slf4j
public class CsvItemReader implements ItemStreamReader<CsvRow> {

    private static final String CURRENT_INDEX_KEY = "csv.reader.current.index";

    private final CsvParserUtil csvParserUtil;

    @Value("#{jobParameters['filePath']}")
    private String filePath;

    private StepExecution stepExecution;
    private List<CsvRow> rows;
    private int currentIndex = 0;

    @BeforeStep
    public void beforeStep(StepExecution stepExecution) {
        this.stepExecution = stepExecution;
    }

    @Override
    public void open(ExecutionContext executionContext) throws ItemStreamException {
        log.info("CsvItemReader: parsing CSV file at {}", filePath);
        try (var stream = new FileInputStream(filePath)) {
            CsvParseResult result = csvParserUtil.parseCsv(stream);

            Set<String> seenBibs = new HashSet<>();
            List<CsvRow> unique = new ArrayList<>();
            StringJoiner duplicates = new StringJoiner(",");

            for (CsvRow row : result.getRows()) {
                if (seenBibs.add(row.getBibNumber())) {
                    unique.add(row);
                } else {
                    duplicates.add(row.getRowNumber() + ":" + row.getBibNumber());
                    log.warn("Duplicate BIB {} at row {}, excluding from import",
                            row.getBibNumber(), row.getRowNumber());
                }
            }

            rows = unique;

            ExecutionContext jobContext = stepExecution.getJobExecution().getExecutionContext();
            jobContext.put("goodiesColumns", String.join(",", result.getGoodiesColumns()));
            jobContext.putInt("totalRows", result.getRows().size());
            stepExecution.getExecutionContext().put("duplicateBibErrors", duplicates.toString());

            if (executionContext.containsKey(CURRENT_INDEX_KEY)) {
                currentIndex = executionContext.getInt(CURRENT_INDEX_KEY);
                log.info("Resuming import from row index {}", currentIndex);
            }

            log.info("CsvItemReader: {} rows to process, {} duplicate BIBs excluded",
                    rows.size(), result.getRows().size() - rows.size());
        } catch (Exception e) {
            throw new ItemStreamException("Failed to parse CSV file: " + filePath, e);
        }
    }

    @Override
    public void update(ExecutionContext executionContext) throws ItemStreamException {
        executionContext.putInt(CURRENT_INDEX_KEY, currentIndex);
    }

    @Override
    public void close() throws ItemStreamException {
        rows = null;
    }

    @Override
    public CsvRow read() {
        if (rows == null || currentIndex >= rows.size()) {
            return null;
        }
        return rows.get(currentIndex++);
    }
}
