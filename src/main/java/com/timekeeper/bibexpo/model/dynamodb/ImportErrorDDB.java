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

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class ImportErrorDDB {

    @Getter(onMethod_ = @DynamoDbPartitionKey)
    private String importId;

    @Getter(onMethod_ = @DynamoDbSortKey)
    private Integer rowNumber;

    @Getter(onMethod_ = @DynamoDbSecondarySortKey(indexNames = "LSI-ErrorTypeIndex"))
    private String errorType;

    @Getter(onMethod_ = @DynamoDbSecondarySortKey(indexNames = "LSI-FieldIndex"))
    private String field;

    private String message;

    @Getter(onMethod_ = @DynamoDbSecondarySortKey(indexNames = "LSI-TimestampIndex"))
    private Long timestamp;

    private Long expirationTime;

    public static ImportErrorDDB create(String importId, Integer rowNumber, String errorType, String field, String message, int ttlDays) {
        long now = Instant.now().getEpochSecond();
        long expiration = now + ((long) ttlDays * 24 * 60 * 60);

        return ImportErrorDDB.builder()
                .importId(importId)
                .rowNumber(rowNumber)
                .errorType(errorType)
                .field(field)
                .message(message)
                .timestamp(now)
                .expirationTime(expiration)
                .build();
    }
}
