package com.timekeeper.bibexpo.model.dynamodb;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class AiChatMessageDDB {

    /** Partition — one continuous conversation per user, e.g. "user-123". */
    @Getter(onMethod_ = @DynamoDbPartitionKey)
    private String conversationId;

    /** Sort key — 0-based turn index; numeric so a query returns turns in chronological order. */
    @Getter(onMethod_ = @DynamoDbSortKey)
    private Integer position;

    private String id;

    private String messageType;

    private String content;

    private String createdAt;

    /** Epoch seconds at which DynamoDB will auto-delete this row (TTL). */
    private Long expirationTime;
}
