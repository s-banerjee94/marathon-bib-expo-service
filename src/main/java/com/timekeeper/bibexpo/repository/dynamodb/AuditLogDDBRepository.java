package com.timekeeper.bibexpo.repository.dynamodb;

import com.timekeeper.bibexpo.config.DynamoDbProperties;
import com.timekeeper.bibexpo.model.dto.audit.AuditLogQuery;
import com.timekeeper.bibexpo.model.dynamodb.AuditLogDDB;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.Instant;
import java.util.Map;

@Repository
@RequiredArgsConstructor
@Slf4j
public class AuditLogDDBRepository {

    public static final String INDEX_ACTION = "LSI-ActionTimeIndex";
    public static final String INDEX_ENTITY_TYPE = "LSI-EntityTypeTimeIndex";
    public static final String INDEX_ACTOR = "LSI-ActorTimeIndex";

    /** Sentinel char that sorts after every valid sort-key character — used as upper-bound suffix. */
    private static final String UPPER_BOUND = "￿";

    private final DynamoDbEnhancedClient dynamoDbEnhancedClient;
    private final DynamoDbProperties dynamoDbProperties;
    private volatile DynamoDbTable<AuditLogDDB> table;

    private DynamoDbTable<AuditLogDDB> getTable() {
        if (table == null) {
            synchronized (this) {
                if (table == null) {
                    table = dynamoDbEnhancedClient.table(dynamoDbProperties.auditLogTable(), TableSchema.fromBean(AuditLogDDB.class));
                }
            }
        }
        return table;
    }

    public void save(AuditLogDDB entry) {
        getTable().putItem(entry);
    }

    /**
     * One direct DynamoDB Query, newest first. The partition is the organization; a single
     * indexed dimension (entityType+entityId, username, action, or entityType) becomes the
     * sort-key prefix, optionally narrowed by a date range. Everything is a key condition —
     * no FilterExpression, so the page size equals the read size.
     *
     * <p>Index selection: {@code username} → actor index; else {@code action} → action index;
     * else {@code entityType} → entity-type index; else the base table (date range only).
     */
    public Page<AuditLogDDB> query(AuditLogQuery q, int limit, Map<String, AttributeValue> exclusiveStartKey) {
        String indexName = null;
        String skPrefix = null;

        if (q.getUsername() != null && !q.getUsername().isBlank()) {
            indexName = INDEX_ACTOR;
            skPrefix = q.getUsername() + "#";
        } else if (q.getAction() != null) {
            indexName = INDEX_ACTION;
            skPrefix = q.getAction().name() + "#";
        } else if (q.getEntityType() != null) {
            indexName = INDEX_ENTITY_TYPE;
            skPrefix = q.getEntityType().name() + "#";
        }

        QueryConditional keyCondition = buildKeyCondition(q.getOrganizationId(), skPrefix, q.getFrom(), q.getTo());

        QueryEnhancedRequest.Builder reqBuilder = QueryEnhancedRequest.builder()
                .queryConditional(keyCondition)
                .scanIndexForward(false)
                .limit(limit);
        if (exclusiveStartKey != null && !exclusiveStartKey.isEmpty()) {
            reqBuilder.exclusiveStartKey(exclusiveStartKey);
        }

        SdkIterable<Page<AuditLogDDB>> pages = (indexName == null)
                ? getTable().query(reqBuilder.build())
                : getTable().index(indexName).query(reqBuilder.build());

        return pages.stream().findFirst().orElse(null);
    }

    private QueryConditional buildKeyCondition(Long orgId, String skPrefix, Instant from, Instant to) {
        if (skPrefix == null && from == null && to == null) {
            return QueryConditional.keyEqualTo(Key.builder().partitionValue(orgId).build());
        }
        String prefix = skPrefix == null ? "" : skPrefix;
        String lo = prefix + (from != null ? from.toString() : "");
        String hi = prefix + (to != null ? to.toString() : "") + UPPER_BOUND;
        return QueryConditional.sortBetween(
                Key.builder().partitionValue(orgId).sortValue(lo).build(),
                Key.builder().partitionValue(orgId).sortValue(hi).build()
        );
    }
}
