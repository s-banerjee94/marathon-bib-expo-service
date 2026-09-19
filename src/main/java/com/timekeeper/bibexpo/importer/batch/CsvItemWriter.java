package com.timekeeper.bibexpo.importer.batch;

import com.timekeeper.bibexpo.participant.api.ParticipantStore;
import com.timekeeper.bibexpo.participant.model.dynamodb.ParticipantDDB;
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

    private final ParticipantStore participantStore;

    @Override
    public void write(Chunk<? extends ParticipantDDB> chunk) {
        if (chunk.isEmpty()) return;
        participantStore.batchSave(new ArrayList<>(chunk.getItems()));
        log.debug("Written chunk of {} participants to DynamoDB", chunk.size());
    }
}
