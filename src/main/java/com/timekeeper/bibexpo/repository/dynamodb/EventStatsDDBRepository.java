package com.timekeeper.bibexpo.repository.dynamodb;

import com.timekeeper.bibexpo.model.dynamodb.EventStatsDDB;
import jakarta.annotation.PreDestroy;
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
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

@Repository
@RequiredArgsConstructor
@Slf4j
public class EventStatsDDBRepository {

    private static final String TABLE_NAME = "marathon-event-stats";
    private static final int BATCH_SIZE = 25;
    private static final int UPDATE_PARALLELISM = 8;

    private final DynamoDbEnhancedClient dynamoDbEnhancedClient;
    private final DynamoDbClient dynamoDbClient;

    private volatile DynamoDbTable<EventStatsDDB> table;
    private volatile ExecutorService updateExecutor;

    private DynamoDbTable<EventStatsDDB> getTable() {
        if (table == null) {
            synchronized (this) {
                if (table == null) {
                    table = dynamoDbEnhancedClient.table(TABLE_NAME, TableSchema.fromBean(EventStatsDDB.class));
                }
            }
        }
        return table;
    }

    private ExecutorService getUpdateExecutor() {
        if (updateExecutor == null) {
            synchronized (this) {
                if (updateExecutor == null) {
                    updateExecutor = Executors.newFixedThreadPool(UPDATE_PARALLELISM);
                }
            }
        }
        return updateExecutor;
    }

    @PreDestroy
    public void shutdown() {
        if (updateExecutor != null) {
            updateExecutor.shutdown();
        }
    }

    public void applyDeltas(String eventId, Map<String, CounterDelta> deltas) {
        if (deltas == null || deltas.isEmpty()) return;

        String now = Instant.now().toString();
        List<CompletableFuture<Void>> futures = deltas.entrySet().stream()
                .map(entry -> CompletableFuture.runAsync(
                        () -> applySingleDelta(eventId, entry.getKey(), entry.getValue(), now),
                        getUpdateExecutor()))
                .toList();

        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } catch (Exception ex) {
            log.warn("Counter delta batch had failures for event {}: {}", eventId, ex.getMessage());
        }
    }

    private void applySingleDelta(String eventId, String statKey, CounterDelta delta, String updatedAt) {
        try {
            Map<String, AttributeValue> key = Map.of(
                    "eventId", AttributeValue.fromS(eventId),
                    "statKey", AttributeValue.fromS(statKey)
            );

            Map<String, String> exprNames = new HashMap<>();
            exprNames.put("#c", "count");
            exprNames.put("#u", "updatedAt");

            Map<String, AttributeValue> exprValues = new HashMap<>();
            exprValues.put(":d", AttributeValue.fromN(String.valueOf(delta.delta())));
            exprValues.put(":u", AttributeValue.fromS(updatedAt));

            StringBuilder expr = new StringBuilder("ADD #c :d SET #u = :u");

            if (delta.raceName() != null) {
                exprNames.put("#rn", "raceName");
                exprValues.put(":rn", AttributeValue.fromS(delta.raceName()));
                expr.append(", #rn = if_not_exists(#rn, :rn)");
            }
            if (delta.categoryName() != null) {
                exprNames.put("#cn", "categoryName");
                exprValues.put(":cn", AttributeValue.fromS(delta.categoryName()));
                expr.append(", #cn = if_not_exists(#cn, :cn)");
            }

            UpdateItemRequest request = UpdateItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .key(key)
                    .updateExpression(expr.toString())
                    .expressionAttributeNames(exprNames)
                    .expressionAttributeValues(exprValues)
                    .build();

            dynamoDbClient.updateItem(request);
        } catch (Exception ex) {
            log.warn("Counter update failed eventId={} statKey={} delta={} err={}",
                    eventId, statKey, delta.delta(), ex.getMessage());
        }
    }

    public List<EventStatsDDB> queryAll(String eventId) {
        QueryConditional queryConditional = QueryConditional.keyEqualTo(
                Key.builder().partitionValue(eventId).build()
        );
        return getTable().query(queryConditional).stream()
                .flatMap(page -> page.items().stream())
                .toList();
    }

    public void putAll(List<EventStatsDDB> rows) {
        if (rows == null || rows.isEmpty()) return;

        chunkedBatchWrite(
                rows,
                WriteBatch.Builder::addPutItem,
                result -> result.unprocessedPutItemsForTable(getTable()),
                getTable()::putItem
        );
        log.debug("Put {} stats rows", rows.size());
    }

    public void deleteAllByEventId(String eventId) {
        List<EventStatsDDB> all = queryAll(eventId);
        if (all.isEmpty()) return;

        int deleted = chunkedBatchWrite(
                all,
                WriteBatch.Builder::addDeleteItem,
                result -> result.unprocessedDeleteItemsForTable(getTable()),
                null
        );
        log.info("Deleted {} stats rows for event {}", deleted, eventId);
    }

    private <U> int chunkedBatchWrite(
            List<EventStatsDDB> rows,
            BiConsumer<WriteBatch.Builder<EventStatsDDB>, EventStatsDDB> op,
            Function<BatchWriteResult, List<U>> unprocessedFn,
            Consumer<U> retryFn
    ) {
        int processed = 0;
        for (int i = 0; i < rows.size(); i += BATCH_SIZE) {
            List<EventStatsDDB> batch = rows.subList(i, Math.min(i + BATCH_SIZE, rows.size()));

            WriteBatch.Builder<EventStatsDDB> batchBuilder = WriteBatch.builder(EventStatsDDB.class)
                    .mappedTableResource(getTable());
            batch.forEach(item -> op.accept(batchBuilder, item));

            BatchWriteResult result = dynamoDbEnhancedClient.batchWriteItem(
                    BatchWriteItemEnhancedRequest.builder()
                            .writeBatches(batchBuilder.build())
                            .build()
            );

            List<U> unprocessed = unprocessedFn.apply(result);
            processed += batch.size() - unprocessed.size();

            if (retryFn != null && !unprocessed.isEmpty()) {
                log.warn("Stats batch had {} unprocessed items, retrying individually", unprocessed.size());
                unprocessed.forEach(retryFn);
                processed += unprocessed.size();
            }
        }
        return processed;
    }

    public record CounterDelta(long delta, String raceName, String categoryName) {}
}
