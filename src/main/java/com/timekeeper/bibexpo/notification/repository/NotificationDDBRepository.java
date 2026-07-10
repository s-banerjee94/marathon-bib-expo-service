package com.timekeeper.bibexpo.notification.repository;

import com.timekeeper.bibexpo.notification.model.dynamodb.NotificationDDB;
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
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.Select;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
@Slf4j
public class NotificationDDBRepository {

    public static final String TABLE_NAME = "marathon-notifications";
    public static final String INDEX_UNREAD = "LSI-UnreadIndex";
    private static final int MAX_BATCH = 25;

    private final DynamoDbEnhancedClient dynamoDbEnhancedClient;
    private final DynamoDbClient dynamoDbClient;
    private volatile DynamoDbTable<NotificationDDB> table;

    private DynamoDbTable<NotificationDDB> getTable() {
        if (table == null) {
            synchronized (this) {
                if (table == null) {
                    table = dynamoDbEnhancedClient.table(TABLE_NAME, TableSchema.fromBean(NotificationDDB.class));
                }
            }
        }
        return table;
    }

    /** Writes one row per recipient in BatchWriteItem chunks of 25. */
    public void saveAll(List<NotificationDDB> items) {
        for (int i = 0; i < items.size(); i += MAX_BATCH) {
            List<NotificationDDB> chunk = items.subList(i, Math.min(i + MAX_BATCH, items.size()));
            WriteBatch.Builder<NotificationDDB> batch = WriteBatch.builder(NotificationDDB.class)
                    .mappedTableResource(getTable());
            chunk.forEach(batch::addPutItem);

            BatchWriteResult result = dynamoDbEnhancedClient.batchWriteItem(
                    BatchWriteItemEnhancedRequest.builder().addWriteBatch(batch.build()).build());

            List<NotificationDDB> unprocessed = result.unprocessedPutItemsForTable(getTable());
            if (!unprocessed.isEmpty()) {
                log.warn("{} notification row(s) left unprocessed by BatchWriteItem", unprocessed.size());
            }
        }
    }

    /** One direct Query on the user's partition, newest first, page-limited and cursor-resumable. */
    public Page<NotificationDDB> queryByUser(Long userId, int limit, Map<String, AttributeValue> exclusiveStartKey) {
        QueryConditional keyCondition = QueryConditional.keyEqualTo(Key.builder().partitionValue(userId).build());
        QueryEnhancedRequest.Builder request = QueryEnhancedRequest.builder()
                .queryConditional(keyCondition)
                .scanIndexForward(false)
                .limit(limit);
        if (exclusiveStartKey != null && !exclusiveStartKey.isEmpty()) {
            request.exclusiveStartKey(exclusiveStartKey);
        }
        return getTable().query(request.build()).stream().findFirst().orElse(null);
    }

    /**
     * Counts unread rows via the sparse {@link #INDEX_UNREAD} (only unread rows carry the index key)
     * using a native {@code Select=COUNT} query, so DynamoDB returns just the number rather than the
     * item bodies. Pages through the low-level {@code lastEvaluatedKey} for users past the 1&nbsp;MB scan.
     */
    public long countUnread(Long userId) {
        AttributeValue uid = AttributeValue.builder().n(String.valueOf(userId)).build();
        long count = 0;
        Map<String, AttributeValue> startKey = null;
        do {
            QueryRequest.Builder request = QueryRequest.builder()
                    .tableName(TABLE_NAME)
                    .indexName(INDEX_UNREAD)
                    .select(Select.COUNT)
                    .keyConditionExpression("userId = :uid")
                    .expressionAttributeValues(Map.of(":uid", uid));
            if (startKey != null) {
                request.exclusiveStartKey(startKey);
            }
            QueryResponse response = dynamoDbClient.query(request.build());
            count += response.count();
            startKey = response.hasLastEvaluatedKey() ? response.lastEvaluatedKey() : null;
        } while (startKey != null);
        return count;
    }

    /**
     * Idempotent: flips one row to read and drops its {@code unreadKey} so it leaves the unread index.
     * A conditional single-attribute {@code UpdateItem} (no read-modify-write) means it neither races
     * with a concurrent update nor depends on what the index projects.
     */
    public void markRead(Long userId, String notificationKey) {
        updateToRead(userId, notificationKey);
    }

    /** Marks every unread row read; returns how many were actually flipped. */
    public int markAllRead(Long userId) {
        QueryConditional keyCondition = QueryConditional.keyEqualTo(Key.builder().partitionValue(userId).build());
        int updated = 0;
        for (Page<NotificationDDB> page : getTable().index(INDEX_UNREAD)
                .query(QueryEnhancedRequest.builder().queryConditional(keyCondition).build())) {
            for (NotificationDDB item : page.items()) {
                if (updateToRead(userId, item.getNotificationKey())) {
                    updated++;
                }
            }
        }
        return updated;
    }

    /**
     * Sets {@code read=true} and removes {@code unreadKey}, only while the row is still unread. Returns
     * false (no-op) when it is already read or absent — keeping {@link #markRead} idempotent and
     * {@link #markAllRead}'s count accurate even if another thread flips the same row concurrently.
     */
    private boolean updateToRead(Long userId, String notificationKey) {
        try {
            dynamoDbClient.updateItem(UpdateItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .key(primaryKey(userId, notificationKey))
                    .updateExpression("SET #read = :true REMOVE unreadKey")
                    .conditionExpression("attribute_exists(unreadKey)")
                    .expressionAttributeNames(Map.of("#read", "read"))
                    .expressionAttributeValues(Map.of(":true", AttributeValue.builder().bool(true).build()))
                    .build());
            return true;
        } catch (ConditionalCheckFailedException e) {
            return false;
        }
    }

    private Map<String, AttributeValue> primaryKey(Long userId, String notificationKey) {
        return Map.of(
                "userId", AttributeValue.builder().n(String.valueOf(userId)).build(),
                "notificationKey", AttributeValue.builder().s(notificationKey).build());
    }

    /** Removes every notification for a user — called when the user is deleted. */
    public int deleteAllByUser(Long userId) {
        QueryConditional keyCondition = QueryConditional.keyEqualTo(Key.builder().partitionValue(userId).build());
        int deleted = 0;
        for (Page<NotificationDDB> page : getTable()
                .query(QueryEnhancedRequest.builder().queryConditional(keyCondition).build())) {
            for (NotificationDDB item : page.items()) {
                getTable().deleteItem(Key.builder()
                        .partitionValue(userId).sortValue(item.getNotificationKey()).build());
                deleted++;
            }
        }
        return deleted;
    }
}
