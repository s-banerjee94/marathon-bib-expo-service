package com.timekeeper.bibexpo.participant.repository;

import com.timekeeper.bibexpo.participant.api.ParticipantStore;
import com.timekeeper.bibexpo.participant.exception.BibNumberAlreadyExistsException;
import com.timekeeper.bibexpo.participant.exception.ParticipantChangedException;
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
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.ReadBatch;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactDeleteItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactPutItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.WriteBatch;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private static final int GET_BATCH_SIZE = 100;
    private static final String VERSION = "version";
    private static final Expression BIB_FREE = Expression.builder().expression("attribute_not_exists(bibNumber)").build();

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

    @Override
    public boolean existsByEventAndBib(Long eventId, String bibNumber) {
        return findByEventAndBib(eventId, bibNumber) != null;
    }

    @Override
    public Set<String> findExistingBibs(Long eventId, Collection<String> bibNumbers) {
        List<String> bibs = bibNumbers.stream().filter(bib -> bib != null && !bib.isBlank()).distinct().toList();
        Set<String> found = new HashSet<>();
        // The client re-requests whatever a call leaves unprocessed as the pages are read.
        for (int i = 0; i < bibs.size(); i += GET_BATCH_SIZE) {
            ReadBatch.Builder<ParticipantDDB> batch = ReadBatch.builder(ParticipantDDB.class)
                    .mappedTableResource(getTable());
            bibs.subList(i, Math.min(i + GET_BATCH_SIZE, bibs.size()))
                    .forEach(bib -> batch.addGetItem(keyOf(eventId, bib)));
            dynamoDbEnhancedClient.batchGetItem(request -> request.readBatches(batch.build()))
                    .resultsForTable(getTable())
                    .forEach(participant -> found.add(participant.getBibNumber()));
        }
        return found;
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
    public void update(ParticipantDDB participant) {
        Long read = participant.getVersion();
        participant.setVersion(nextVersion(read));
        try {
            getTable().putItem(PutItemEnhancedRequest.builder(ParticipantDDB.class)
                    .item(participant)
                    .conditionExpression(unchangedSince(read))
                    .build());
        } catch (ConditionalCheckFailedException e) {
            throw new ParticipantChangedException();
        }
        log.debug("Updated participant with bib {} for event {}",
                participant.getBibNumber(), participant.getEventId());
    }

    /**
     * Writes a new participant only if its bib is still free. DynamoDB checks and writes in one step,
     * so of two requests racing for the same bib exactly one wins and the other gets the conflict.
     *
     * @param participant the record to write
     * @throws BibNumberAlreadyExistsException if the bib is already taken in the event
     */
    public void create(ParticipantDDB participant) {
        participant.setVersion(1L);
        try {
            getTable().putItem(PutItemEnhancedRequest.builder(ParticipantDDB.class)
                    .item(participant)
                    .conditionExpression(BIB_FREE)
                    .build());
        } catch (ConditionalCheckFailedException e) {
            throw new BibNumberAlreadyExistsException();
        }
    }

    /**
     * Moves a participant to the bib it now carries in one transaction: the new bib must be free and the
     * record under the old bib unchanged since it was read, or nothing is written.
     *
     * @param participant  the record, already carrying its new bib number
     * @param oldBibNumber the bib it is moving from
     * @throws BibNumberAlreadyExistsException if the new bib is already taken in the event
     * @throws ParticipantChangedException     if the old record was changed or deleted since it was read
     */
    public void changeBib(ParticipantDDB participant, String oldBibNumber) {
        Long read = participant.getVersion();
        participant.setVersion(nextVersion(read));
        try {
            dynamoDbEnhancedClient.transactWriteItems(TransactWriteItemsEnhancedRequest.builder()
                    .addPutItem(getTable(), TransactPutItemEnhancedRequest.builder(ParticipantDDB.class)
                            .item(participant)
                            .conditionExpression(BIB_FREE)
                            .build())
                    .addDeleteItem(getTable(), TransactDeleteItemEnhancedRequest.builder()
                            .key(Key.builder().partitionValue(participant.getEventId()).sortValue(oldBibNumber).build())
                            .conditionExpression(unchangedSince(read))
                            .build())
                    .build());
        } catch (TransactionCanceledException e) {
            if (e.hasCancellationReasons()
                    && "ConditionalCheckFailed".equals(e.cancellationReasons().get(0).code())) {
                throw new BibNumberAlreadyExistsException();
            }
            throw new ParticipantChangedException();
        }
    }

    /** The record still exists and nobody has written it since it was read at {@code version}. */
    private static Expression unchangedSince(Long version) {
        if (version == null) {
            // Written before records carried a version.
            return Expression.builder()
                    .expression("attribute_exists(bibNumber) AND attribute_not_exists(#version)")
                    .putExpressionName("#version", VERSION)
                    .build();
        }
        return Expression.builder()
                .expression("#version = :version")
                .putExpressionName("#version", VERSION)
                .putExpressionValue(":version", AttributeValue.fromN(version.toString()))
                .build();
    }

    private static long nextVersion(Long version) {
        return version == null ? 1 : version + 1;
    }

    /**
     * Sets only the verifyShortCode attribute via a targeted update, leaving every other attribute
     * untouched. It raises the version too, so a copy read before it cannot be written back over the code.
     * A participant deleted meanwhile is skipped.
     */
    @Override
    public void updateVerifyShortCode(Long eventId, String bibNumber, String code) {
        try {
            dynamoDbClient.updateItem(UpdateItemRequest.builder()
                    .tableName(dynamoDbProperties.participantsTable())
                    .key(primaryKey(String.valueOf(eventId), bibNumber))
                    .updateExpression("SET verifyShortCode = :code ADD #version :one")
                    .conditionExpression("attribute_exists(bibNumber)")
                    .expressionAttributeNames(Map.of("#version", VERSION))
                    .expressionAttributeValues(Map.of(":code", AttributeValue.fromS(code), ":one", AttributeValue.fromN("1")))
                    .build());
        } catch (ConditionalCheckFailedException e) {
            log.debug("Participant with bib {} for event {} was deleted before its short code was set", bibNumber, eventId);
        }
    }

    @Override
    public void batchSave(List<ParticipantDDB> participants) {
        if (participants == null || participants.isEmpty()) return;

        // ponytail: a batch write cannot be conditional, so it overwrites anything changed since its read; only the
        // import and the draft-only goody purge use it. Raising the version still stops older copies landing over it.
        participants.forEach(participant -> participant.setVersion(nextVersion(participant.getVersion())));

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
                logUnprocessed("put", unprocessed.size());
                unprocessed.forEach(getTable()::putItem);
            }
        }

        log.debug("Batch saved {} participants to DynamoDB", participants.size());
    }

    @Override
    public int deleteAllByEventId(String eventId) {
        return deleteAll(findAllByEventId(eventId));
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
     * @return how many were deleted
     */
    public int deleteAll(List<ParticipantDDB> participants) {
        if (participants == null || participants.isEmpty()) {
            return 0;
        }

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
            List<Key> unprocessed = result.unprocessedDeleteItemsForTable(getTable());
            if (!unprocessed.isEmpty()) {
                logUnprocessed("delete", unprocessed.size());
                unprocessed.forEach(key -> getTable().deleteItem(key));
            }
        }
        return participants.size();
    }

    private static void logUnprocessed(String operation, int count) {
        log.atWarn()
                .addKeyValue("event", "participant.batch_unprocessed")
                .addKeyValue("operation", operation)
                .addKeyValue("count", count)
                .log("DynamoDB left batch items unprocessed; retrying them one by one");
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
