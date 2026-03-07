package com.timekeeper.bibexpo.repository.dynamodb;

import com.timekeeper.bibexpo.exception.ParticipantNotFoundException;
import com.timekeeper.bibexpo.model.dynamodb.ParticipantDDB;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.BatchWriteItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.BatchWriteResult;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.WriteBatch;

import java.util.List;

@Repository
@RequiredArgsConstructor
@Slf4j
public class ParticipantDDBRepository {

    private final DynamoDbEnhancedClient dynamoDbEnhancedClient;
    private DynamoDbTable<ParticipantDDB> table;

    @PostConstruct
    public void init() {
        this.table = dynamoDbEnhancedClient.table(
                "marathon-participants",
                TableSchema.fromBean(ParticipantDDB.class)
        );
    }

    public ParticipantDDB findByEventAndBibOrThrow(Long eventId, String bibNumber) {
        String eventIdStr = String.valueOf(eventId);
        Key key = Key.builder()
                .partitionValue(eventIdStr)
                .sortValue(bibNumber)
                .build();

        ParticipantDDB participant = table.getItem(key);
        if (participant == null) {
            throw new ParticipantNotFoundException(eventIdStr, bibNumber);
        }

        log.debug("Found participant with bib {} for event {}", bibNumber, eventId);
        return participant;
    }

    public void save(ParticipantDDB participant) {
        table.putItem(participant);
        log.debug("Saved participant with bib {} for event {}",
                participant.getBibNumber(), participant.getEventId());
    }

    public void batchSave(List<ParticipantDDB> participants) {
        if (participants == null || participants.isEmpty()) return;

        int batchSize = 25;
        for (int i = 0; i < participants.size(); i += batchSize) {
            List<ParticipantDDB> batch = participants.subList(i, Math.min(i + batchSize, participants.size()));

            WriteBatch.Builder<ParticipantDDB> batchBuilder = WriteBatch.builder(ParticipantDDB.class)
                    .mappedTableResource(table);
            batch.forEach(batchBuilder::addPutItem);

            BatchWriteResult result = dynamoDbEnhancedClient.batchWriteItem(
                    BatchWriteItemEnhancedRequest.builder()
                            .writeBatches(batchBuilder.build())
                            .build()
            );

            List<ParticipantDDB> unprocessed = result.unprocessedPutItemsForTable(table);
            if (!unprocessed.isEmpty()) {
                log.warn("DynamoDB batch write had {} unprocessed items, retrying individually", unprocessed.size());
                unprocessed.forEach(p -> {
                    table.putItem(p);
                    log.debug("Retry-saved participant bib {} for event {}", p.getBibNumber(), p.getEventId());
                });
            }
        }

        log.debug("Batch saved {} participants to DynamoDB", participants.size());
    }

    public int deleteAllByEventId(String eventId) {
        QueryConditional queryConditional = QueryConditional.keyEqualTo(
                Key.builder().partitionValue(eventId).build()
        );

        List<ParticipantDDB> all = table.query(queryConditional).stream()
                .flatMap(page -> page.items().stream())
                .toList();

        if (all.isEmpty()) {
            return 0;
        }

        int deleted = 0;
        int batchSize = 25;
        for (int i = 0; i < all.size(); i += batchSize) {
            List<ParticipantDDB> batch = all.subList(i, Math.min(i + batchSize, all.size()));
            WriteBatch.Builder<ParticipantDDB> batchBuilder = WriteBatch.builder(ParticipantDDB.class)
                    .mappedTableResource(table);
            batch.forEach(batchBuilder::addDeleteItem);
            BatchWriteResult result = dynamoDbEnhancedClient.batchWriteItem(
                    BatchWriteItemEnhancedRequest.builder()
                            .writeBatches(batchBuilder.build())
                            .build()
            );
            deleted += batch.size() - result.unprocessedDeleteItemsForTable(table).size();
        }

        log.info("Deleted {} participants for event {}", deleted, eventId);
        return deleted;
    }

    public DynamoDbTable<ParticipantDDB> getTable() {
        return table;
    }
}
