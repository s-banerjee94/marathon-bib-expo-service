package com.timekeeper.bibexpo.repository.dynamodb;

import com.timekeeper.bibexpo.model.dynamodb.AiChatMessageDDB;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.BatchWriteItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.WriteBatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;

@Repository
@RequiredArgsConstructor
@Slf4j
public class AiChatMessageDDBRepository {

    private static final String TABLE_NAME = "marathon-ai-chat-memory";
    private static final int BATCH_SIZE = 25;

    private final DynamoDbEnhancedClient dynamoDbEnhancedClient;

    private volatile DynamoDbTable<AiChatMessageDDB> table;

    private DynamoDbTable<AiChatMessageDDB> getTable() {
        if (table == null) {
            synchronized (this) {
                if (table == null) {
                    table = dynamoDbEnhancedClient.table(TABLE_NAME, TableSchema.fromBean(AiChatMessageDDB.class));
                }
            }
        }
        return table;
    }

    /** All turns of a conversation, oldest first (ascending position). */
    public List<AiChatMessageDDB> findByConversationId(String conversationId) {
        return getTable().query(QueryConditional.keyEqualTo(
                        Key.builder().partitionValue(conversationId).build()))
                .stream()
                .flatMap(page -> page.items().stream())
                .toList();
    }

    /** The most recent turn of a conversation (highest position), if any. */
    public Optional<AiChatMessageDDB> findLastByConversationId(String conversationId) {
        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.keyEqualTo(
                        Key.builder().partitionValue(conversationId).build()))
                .scanIndexForward(false)
                .limit(1)
                .build();
        return getTable().query(request).stream()
                .flatMap(page -> page.items().stream())
                .findFirst();
    }

    /** Highest position currently stored for a conversation, or -1 when it is empty. */
    public int findMaxPosition(String conversationId) {
        return findLastByConversationId(conversationId)
                .map(AiChatMessageDDB::getPosition)
                .orElse(-1);
    }

    /** The most recent {@code n} turns, returned oldest-first (the model's context window). */
    public List<AiChatMessageDDB> findLastN(String conversationId, int n) {
        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.keyEqualTo(
                        Key.builder().partitionValue(conversationId).build()))
                .scanIndexForward(false)
                .limit(n)
                .build();
        List<AiChatMessageDDB> newestFirst = new ArrayList<>(getTable().query(request).stream()
                .flatMap(page -> page.items().stream())
                .limit(n)
                .toList());
        Collections.reverse(newestFirst);
        return newestFirst;
    }

    /**
     * One page of history for the conversation, newest-first. Pass {@code beforePosition} null for the
     * newest page, or a prior page's cursor to walk backwards into older turns.
     */
    public List<AiChatMessageDDB> findPage(String conversationId, Integer beforePosition, int limit) {
        QueryConditional conditional = (beforePosition == null)
                ? QueryConditional.keyEqualTo(Key.builder().partitionValue(conversationId).build())
                : QueryConditional.sortLessThan(Key.builder()
                        .partitionValue(conversationId).sortValue(beforePosition).build());
        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                .queryConditional(conditional)
                .scanIndexForward(false)
                .limit(limit)
                .build();
        return getTable().query(request).stream()
                .flatMap(page -> page.items().stream())
                .limit(limit)
                .toList();
    }

    public void putAll(List<AiChatMessageDDB> items) {
        if (items.isEmpty()) {
            return;
        }
        batchWrite(items, WriteBatch.Builder::addPutItem);
    }

    public void deleteByConversationId(String conversationId) {
        List<AiChatMessageDDB> existing = findByConversationId(conversationId);
        if (existing.isEmpty()) {
            return;
        }
        batchWrite(existing, WriteBatch.Builder::addDeleteItem);
    }

    /** Distinct conversation ids; a table scan, used only for housekeeping, never on the chat path. */
    public List<String> findConversationIds() {
        return getTable().scan().stream()
                .flatMap(page -> page.items().stream())
                .map(AiChatMessageDDB::getConversationId)
                .distinct()
                .toList();
    }

    private void batchWrite(List<AiChatMessageDDB> items,
                            BiConsumer<WriteBatch.Builder<AiChatMessageDDB>, AiChatMessageDDB> op) {
        for (int i = 0; i < items.size(); i += BATCH_SIZE) {
            List<AiChatMessageDDB> chunk = items.subList(i, Math.min(i + BATCH_SIZE, items.size()));

            WriteBatch.Builder<AiChatMessageDDB> batch = WriteBatch.builder(AiChatMessageDDB.class)
                    .mappedTableResource(getTable());
            chunk.forEach(item -> op.accept(batch, item));

            dynamoDbEnhancedClient.batchWriteItem(BatchWriteItemEnhancedRequest.builder()
                    .writeBatches(batch.build())
                    .build());
        }
    }
}
