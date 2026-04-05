package com.timekeeper.bibexpo.batch;

import com.timekeeper.bibexpo.util.CsvParseResult;
import com.timekeeper.bibexpo.util.CsvParserUtil;
import com.timekeeper.bibexpo.util.CsvRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;

@Component
@StepScope
@RequiredArgsConstructor
@Slf4j
public class CsvItemReader implements ItemReader<CsvRow>, StepExecutionListener {

    private final CsvParserUtil csvParserUtil;

    @Value("#{jobParameters['filePath']}")
    private String filePath;

    private List<CsvRow> rows;
    private int index = 0;

    @Override
    public void beforeStep(StepExecution stepExecution) {
        log.info("CsvItemReader: parsing CSV file at {}", filePath);
        try (var stream = new FileInputStream(filePath)) {
            CsvParseResult result = csvParserUtil.parseCsv(stream);
            stepExecution.getJobExecution().getExecutionContext()
                    .put("goodiesColumns", String.join(",", result.getGoodiesColumns()));

            Set<String> seenBibs = new HashSet<>();
            List<CsvRow> unique = new ArrayList<>();
            StringJoiner duplicates = new StringJoiner(",");

            for (CsvRow row : result.getRows()) {
                if (seenBibs.add(row.getBibNumber())) {
                    unique.add(row);
                } else {
                    duplicates.add(row.getRowNumber() + ":" + row.getBibNumber());
                    log.warn("CsvItemReader: duplicate BIB {} at row {}, excluding from import",
                            row.getBibNumber(), row.getRowNumber());
                }
            }

            rows = unique;
            stepExecution.getExecutionContext().put("duplicateBibErrors", duplicates.toString());
            log.info("CsvItemReader: {} rows to process, {} duplicate BIBs excluded",
                    rows.size(), result.getRows().size() - rows.size());
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse CSV file: " + filePath, e);
        }
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        return stepExecution.getExitStatus();
    }

    @Override
    public CsvRow read() {
        if (rows == null || index >= rows.size()) {
            return null;
        }
        return rows.get(index++);
    }
}
