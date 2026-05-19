package com.timekeeper.bibexpo.batch;

import com.timekeeper.bibexpo.model.dynamodb.ParticipantDDB;
import com.timekeeper.bibexpo.repository.dynamodb.ParticipantDDBRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

@Component
@StepScope
@RequiredArgsConstructor
@Slf4j
public class CsvItemWriter implements ItemWriter<ParticipantDDB> {

    private final ParticipantDDBRepository repository;

    @Override
    public void write(Chunk<? extends ParticipantDDB> chunk) {
        if (chunk.isEmpty()) return;
        repository.batchSave(new ArrayList<>(chunk.getItems()));
        log.debug("Written chunk of {} participants to DynamoDB", chunk.size());
    }
}
