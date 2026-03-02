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
import java.util.List;

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
            rows = result.getRows();
            stepExecution.getJobExecution().getExecutionContext()
                    .put("goodiesColumns", String.join(",", result.getGoodiesColumns()));
            log.info("CsvItemReader: parsed {} rows, {} goodies columns",
                    rows.size(), result.getGoodiesColumns().size());
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
