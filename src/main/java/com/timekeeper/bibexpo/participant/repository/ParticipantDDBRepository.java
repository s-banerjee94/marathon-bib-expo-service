package com.timekeeper.bibexpo.participant.repository;

import com.timekeeper.bibexpo.participant.api.ParticipantStore;
import com.timekeeper.bibexpo.participant.exception.ParticipantNotFoundException;
import com.timekeeper.bibexpo.participant.model.dynamodb.ParticipantDDB;
import com.timekeeper.bibexpo.shared.persistence.DynamoDbProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

@Repository
@RequiredArgsConstructor
@Slf4j
public class ParticipantDDBRepository implements ParticipantStore {

    private final DynamoDbEnhancedClient dynamoDbEnhancedClient;
    private final DynamoDbClient dynamoDbClient;
    private final DynamoDbProperties dynamoDbProperties;
    private volatile DynamoDbTable<ParticipantDDB> table;

    private static final int BATCH_SIZE = 25;

    private DynamoDbTable<ParticipantDDB> getTable() {
        if (table == null) {
            synchronized (this) {
                if (table == null) {
                    table = dynamoDbEnhancedClient.table(dynamoDbProperties.participantsTable(), TableSchema.fromBean(ParticipantDDB.class));
                }
            }
        }
        return table;
    }

    private static QueryConditional wholeEvent(String eventId) {
        return QueryConditional.keyEqualTo(Key.builder().partitionValue(eventId).build());
    }

    @Override
    public ParticipantDDB findByEventAndBibOrThrow(Long eventId, String bibNumber) {
        ParticipantDDB participant = findByEventAndBib(eventId, bibNumber);
        if (participant == null) {
            throw new ParticipantNotFoundException();
        }

        log.debug("Found participant with bib {} for event {}", bibNumber, eventId);
        return participant;
    }

    /** Same lookup, for callers that treat a missing bib as an answer rather than a failure. */
    public ParticipantDDB findByEventAndBib(Long eventId, String bibNumber) {
        return getTable().getItem(keyOf(eventId, bibNumber));
    }

    public void deleteByEventAndBib(Long eventId, String bibNumber) {
        getTable().deleteItem(keyOf(eventId, bibNumber));
    }

    private static Key keyOf(Long eventId, String bibNumber) {
        return Key.builder()
                .partitionValue(String.valueOf(eventId))
                .sortValue(bibNumber)
                .build();
    }

    private static Map<String, AttributeValue> primaryKey(String eventId, String bibNumber) {
        return Map.of("eventId", AttributeValue.fromS(eventId), "bibNumber", AttributeValue.fromS(bibNumber));
    }

    @Override
    public void save(ParticipantDDB participant) {
        getTable().putItem(participant);
        log.debug("Saved participant with bib {} for event {}",
                participant.getBibNumber(), participant.getEventId());
    }

    /**
     * Sets only the verifyShortCode attribute via a targeted update, leaving every other
     * attribute untouched so a concurrent edit (e.g. a bib collection) is not overwritten.
     */
    @Override
    public void updateVerifyShortCode(Long eventId, String bibNumber, String code) {
        dynamoDbClient.updateItem(UpdateItemRequest.builder()
                .tableName(dynamoDbProperties.participantsTable())
                .key(primaryKey(String.valueOf(eventId), bibNumber))
                .updateExpression("SET verifyShortCode = :code")
                .expressionAttributeValues(Map.of(":code", AttributeValue.fromS(code)))
                .build());
    }

    @Override
    public void batchSave(List<ParticipantDDB> participants) {
        if (participants == null || participants.isEmpty()) return;

        for (int i = 0; i < participants.size(); i += BATCH_SIZE) {
            List<ParticipantDDB> batch = participants.subList(i, Math.min(i + BATCH_SIZE, participants.size()));

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

    @Override
    public int deleteAllByEventId(String eventId) {
        int deleted = deleteAll(findAllByEventId(eventId));
        log.info("Deleted {} participants for event {}", deleted, eventId);
        return deleted;
    }

    /** Every participant of an event, read through as many round trips as it takes. */
    public List<ParticipantDDB> findAllByEventId(String eventId) {
        return getTable().query(wholeEvent(eventId)).stream()
                .flatMap(page -> page.items().stream())
                .toList();
    }

    /**
     * Deletes the given participants in batches of 25.
     *
     * @param participants the records to delete
     * @return how many were deleted; a shortfall means DynamoDB left some unprocessed
     */
    public int deleteAll(List<ParticipantDDB> participants) {
        if (participants == null || participants.isEmpty()) {
            return 0;
        }

        int deleted = 0;
        for (int i = 0; i < participants.size(); i += BATCH_SIZE) {
            List<ParticipantDDB> batch = participants.subList(i, Math.min(i + BATCH_SIZE, participants.size()));
            WriteBatch.Builder<ParticipantDDB> batchBuilder = WriteBatch.builder(ParticipantDDB.class)
                    .mappedTableResource(getTable());
            batch.forEach(batchBuilder::addDeleteItem);
            BatchWriteResult result = dynamoDbEnhancedClient.batchWriteItem(
                    BatchWriteItemEnhancedRequest.builder()
                            .writeBatches(batchBuilder.build())
                            .build()
            );
            int unprocessed = result.unprocessedDeleteItemsForTable(getTable()).size();
            if (unprocessed > 0) {
                log.warn("Delete batch left {} of {} items unprocessed", unprocessed, batch.size());
            }
            deleted += batch.size() - unprocessed;
        }
        return deleted;
    }

    @Override
    public SdkIterable<Page<ParticipantDDB>> findPagesByEventId(Long eventId, int pageSize) {
        return getTable().query(
                QueryEnhancedRequest.builder()
                        .queryConditional(wholeEvent(String.valueOf(eventId)))
                        .limit(pageSize)
                        .build()
        );
    }

    /**
     * One read of an event's participants after the start key. A filter applies after the limit, so a
     * filtered page can come back short and still have more behind it.
     */
    public Page<ParticipantDDB> findPage(Long eventId, int limit,
                                         Map<String, AttributeValue> startKey, Expression filter) {
        return getTable().query(
                        pageRequest(wholeEvent(String.valueOf(eventId)), limit, startKey, filter).build())
                .stream().findFirst().orElse(null);
    }

    @Override
    public Page<ParticipantDDB> fillPage(Long eventId, int limit, Map<String, AttributeValue> startKey,
                                         Expression filter, Predicate<ParticipantDDB> keep) {
        List<ParticipantDDB> kept = new ArrayList<>(limit);
        for (ParticipantDDB participant : getTable()
                .query(pageRequest(wholeEvent(String.valueOf(eventId)), limit, startKey, filter).build())
                .items()) {
            if (!keep.test(participant)) {
                continue;
            }
            // One more passes, so there is a next page, and it starts right after the last one kept.
            if (kept.size() == limit) {
                ParticipantDDB last = kept.get(limit - 1);
                return Page.create(kept, primaryKey(last.getEventId(), last.getBibNumber()));
            }
            kept.add(participant);
        }
        return Page.create(kept, null);
    }

    /**
     * Reads a single page from one of the participant LSIs, for the lookup searches that key on a
     * field other than the bib number.
     *
     * @param indexName   the local secondary index to read
     * @param conditional the key condition for that index
     * @param limit       maximum rows to read
     * @param startKey    exclusive start key from the previous page, or null/empty for the first
     * @return the page, or null when the index holds nothing for this event
     */
    public Page<ParticipantDDB> findPageByIndex(String indexName, QueryConditional conditional, int limit,
                                                Map<String, AttributeValue> startKey) {
        return getTable().index(indexName)
                .query(pageRequest(conditional, limit, startKey, null).build())
                .stream().findFirst().orElse(null);
    }

    private static QueryEnhancedRequest.Builder pageRequest(QueryConditional conditional, int limit,
                                                            Map<String, AttributeValue> startKey, Expression filter) {
        QueryEnhancedRequest.Builder request = QueryEnhancedRequest.builder()
                .queryConditional(conditional)
                .limit(limit);
        if (filter != null) {
            request.filterExpression(filter);
        }
        if (startKey != null && !startKey.isEmpty()) {
            request.exclusiveStartKey(startKey);
        }
        return request;
    }

    /**
     * Counts the participants of an event assigned to one category.
     *
     * @param eventId    the owning event
     * @param categoryId the category to count
     * @return how many participants point at that category
     */
    public long countByEventAndCategory(Long eventId, Long categoryId) {
        Expression filter = Expression.builder()
                .expression("#categoryId = :categoryId")
                .expressionNames(Map.of("#categoryId", "categoryId"))
                .expressionValues(Map.of(":categoryId",
                        AttributeValue.builder().s(categoryId.toString()).build()))
                .build();

        return getTable().query(QueryEnhancedRequest.builder()
                        .queryConditional(wholeEvent(String.valueOf(eventId)))
                        .filterExpression(filter)
                        .build()).stream()
                .mapToLong(page -> page.items().size())
                .sum();
    }

    /**
     * Per-event chip-number uniqueness, via LSI-ChipNumberIndex.
     *
     * @param eventId        the owning event
     * @param chipNumber     the chip number to check
     * @param ownerBibNumber the bib the chip is being assigned to, which may already hold it
     * @return true when a different bib in this event already holds the chip
     */
    public boolean isChipNumberTakenByAnother(Long eventId, String chipNumber, String ownerBibNumber) {
        QueryConditional condition = QueryConditional.keyEqualTo(
                Key.builder().partitionValue(String.valueOf(eventId)).sortValue(chipNumber).build());
        return getTable().index(ParticipantDDB.CHIP_NUMBER_INDEX)
                .query(QueryEnhancedRequest.builder().queryConditional(condition).build())
                .stream()
                .flatMap(page -> page.items().stream())
                .anyMatch(existing -> !existing.getBibNumber().equals(ownerBibNumber));
    }
}
