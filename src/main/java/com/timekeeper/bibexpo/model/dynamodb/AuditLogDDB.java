package com.timekeeper.bibexpo.model.dynamodb;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class AuditLogDDB {

    /** Sentinel partition holding a duplicate copy of every event — used for the ROOT/ADMIN cross-org feed. */
    public static final Long ALL_PARTITION = -1L;

    /** Partition — real org id, 0 for system events without an org, or {@link #ALL_PARTITION} for the cross-org duplicate row. */
    @Getter(onMethod_ = @DynamoDbPartitionKey)
    private Long organizationId;

    /**
     * Sort key — "{ISO instant}#{short uuid}". The ISO prefix is lexicographically sortable
     * (newest first when queried with scanIndexForward=false); the UUID suffix prevents
     * two writes in the same millisecond from overwriting each other.
     */
    @Getter(onMethod_ = @DynamoDbSortKey)
    private String eventKey;

    /** LSI sort key — "{action}#{instant}#{uuid}". Lets us query by action with optional date range. */
    @Getter(onMethod_ = @DynamoDbSecondarySortKey(indexNames = "LSI-ActionTimeIndex"))
    private String actionKey;

    /** LSI sort key — "{entityType}#{instant}#{uuid}". Lets us query by entity type with optional date range. */
    @Getter(onMethod_ = @DynamoDbSecondarySortKey(indexNames = "LSI-EntityTypeTimeIndex"))
    private String entityTypeKey;

    /** LSI sort key — "{actorUsername}#{instant}#{uuid}". Lets us query by username with optional date range. */
    @Getter(onMethod_ = @DynamoDbSecondarySortKey(indexNames = "LSI-ActorTimeIndex"))
    private String actorKey;

    private String id;
    private Long actorUserId;
    private String actorName;
    private String action;
    private String entityType;
    private String entityId;
    private String entityLabel;
    private String description;
    private String createdAt;

    /** Epoch seconds at which DynamoDB will auto-delete this row (TTL). */
    private Long expirationTime;
}
