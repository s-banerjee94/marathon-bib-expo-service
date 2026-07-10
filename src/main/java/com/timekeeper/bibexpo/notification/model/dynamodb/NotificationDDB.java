package com.timekeeper.bibexpo.notification.model.dynamodb;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

/**
 * One in-app notification for a single recipient. Group targets (all admins, a whole org, …) fan out
 * to one row per recipient, so every read is a single-partition query and each recipient owns its
 * read state. Rows auto-expire via TTL ~30 days after creation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class NotificationDDB {

    /** Partition — the recipient user id. */
    @Getter(onMethod_ = @DynamoDbPartitionKey)
    private Long userId;

    /**
     * Sort key — "{ISO instant}#{short uuid}". The ISO prefix is lexicographically sortable
     * (newest first with scanIndexForward=false); the UUID suffix keeps two same-millisecond writes
     * distinct. Also serves as the public notification id used by the read/mark-as-read API.
     */
    @Getter(onMethod_ = @DynamoDbSortKey)
    private String notificationKey;

    /**
     * Sparse LSI sort key — same value as {@link #notificationKey} but set ONLY while the
     * notification is unread, and removed when it is marked read. The index therefore holds only
     * unread rows, so the bell badge is a pure COUNT key-query with no filter.
     */
    @Getter(onMethod_ = @DynamoDbSecondarySortKey(indexNames = "LSI-UnreadIndex"))
    private String unreadKey;

    private String type;
    private String title;
    private String message;
    private String entityType;
    private String entityId;
    private String audience;
    private String actorName;
    private Boolean read;
    private String createdAt;

    /** Epoch seconds at which DynamoDB auto-deletes this row (TTL). */
    private Long expirationTime;
}
