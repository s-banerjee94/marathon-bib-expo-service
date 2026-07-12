package com.timekeeper.bibexpo.repository.dynamodb;

import com.timekeeper.bibexpo.config.DynamoDbProperties;
import com.timekeeper.bibexpo.exception.ParticipantNotFoundException;
import com.timekeeper.bibexpo.model.dynamodb.ParticipantDDB;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.BatchWriteItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.BatchWriteResult;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.WriteBatch;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
@Slf4j
public class ParticipantDDBRepository {

    private final DynamoDbEnhancedClient dynamoDbEnhancedClient;
    private final DynamoDbClient dynamoDbClient;
    private final DynamoDbProperties dynamoDbProperties;
    private volatile DynamoDbTable<ParticipantDDB> table;

    public DynamoDbTable<ParticipantDDB> getTable() {
        if (table == null) {
            synchronized (this) {
                if (table == null) {
                    table = dynamoDbEnhancedClient.table(dynamoDbProperties.participantsTable(), TableSchema.fromBean(ParticipantDDB.class));
                }
            }
        }
        return table;
    }

    public ParticipantDDB findByEventAndBibOrThrow(Long eventId, String bibNumber) {
        String eventIdStr = String.valueOf(eventId);
        Key key = Key.builder()
                .partitionValue(eventIdStr)
                .sortValue(bibNumber)
                .build();

        ParticipantDDB participant = getTable().getItem(key);
        if (participant == null) {
            throw new ParticipantNotFoundException();
        }

        log.debug("Found participant with bib {} for event {}", bibNumber, eventId);
        return participant;
    }

    public void save(ParticipantDDB participant) {
        getTable().putItem(participant);
        log.debug("Saved participant with bib {} for event {}",
                participant.getBibNumber(), participant.getEventId());
    }

    /**
     * Sets only the verifyShortCode attribute via a targeted update, leaving every other
     * attribute untouched so a concurrent edit (e.g. a bib collection) is not overwritten.
     */
    public void updateVerifyShortCode(Long eventId, String bibNumber, String code) {
        dynamoDbClient.updateItem(UpdateItemRequest.builder()
                .tableName(dynamoDbProperties.participantsTable())
                .key(Map.of(
                        "eventId", AttributeValue.fromS(String.valueOf(eventId)),
                        "bibNumber", AttributeValue.fromS(bibNumber)))
                .updateExpression("SET verifyShortCode = :code")
                .expressionAttributeValues(Map.of(":code", AttributeValue.fromS(code)))
                .build());
    }

    public void batchSave(List<ParticipantDDB> participants) {
        if (participants == null || participants.isEmpty()) return;

        int batchSize = 25;
        for (int i = 0; i < participants.size(); i += batchSize) {
            List<ParticipantDDB> batch = participants.subList(i, Math.min(i + batchSize, participants.size()));

            WriteBatch.Builder<ParticipantDDB> batchBuilder = WriteBatch.builder(ParticipantDDB.class)
                    .mappedTableResource(getTable());
            batch.forEach(batchBuilder::addPutItem);

            BatchWriteResult result = dynamoDbEnhancedClient.batchWriteItem(
                    BatchWriteItemEnhancedRequest.builder()
                            .writeBatches(batchBuilder.build())
                            .build()
            );

            List<ParticipantDDB> unprocessed = result.unprocessedPutItemsForTable(getTable());
            if (!unprocessed.isEmpty()) {
                log.warn("DynamoDB batch write had {} unprocessed items, retrying individually", unprocessed.size());
                unprocessed.forEach(p -> {
                    getTable().putItem(p);
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

        List<ParticipantDDB> all = getTable().query(queryConditional).stream()
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
                    .mappedTableResource(getTable());
            batch.forEach(batchBuilder::addDeleteItem);
            BatchWriteResult result = dynamoDbEnhancedClient.batchWriteItem(
                    BatchWriteItemEnhancedRequest.builder()
                            .writeBatches(batchBuilder.build())
                            .build()
            );
            deleted += batch.size() - result.unprocessedDeleteItemsForTable(getTable()).size();
        }

        log.info("Deleted {} participants for event {}", deleted, eventId);
        return deleted;
    }

    public SdkIterable<Page<ParticipantDDB>> findPagesByEventId(Long eventId, int pageSize) {
        QueryConditional queryConditional = QueryConditional.keyEqualTo(
                Key.builder().partitionValue(String.valueOf(eventId)).build()
        );
        return getTable().query(
                QueryEnhancedRequest.builder()
                        .queryConditional(queryConditional)
                        .limit(pageSize)
                        .build()
        );
    }
}
