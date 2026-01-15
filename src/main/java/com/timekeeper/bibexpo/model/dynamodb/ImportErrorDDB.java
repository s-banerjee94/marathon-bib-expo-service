package com.timekeeper.bibexpo.model.dynamodb;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class ImportErrorDDB {

    private String importId;
    private Integer rowNumber;
    private String errorType;
    private String field;
    private String message;
    private Long timestamp;
    private Long expirationTime;

    @DynamoDbPartitionKey
    @DynamoDbAttribute("importId")
    public String getImportId() {
        return importId;
    }

    @DynamoDbSortKey
    @DynamoDbAttribute("rowNumber")
    public Integer getRowNumber() {
        return rowNumber;
    }

    @DynamoDbAttribute("errorType")
    public String getErrorType() {
        return errorType;
    }

    @DynamoDbAttribute("field")
    public String getField() {
        return field;
    }

    @DynamoDbAttribute("message")
    public String getMessage() {
        return message;
    }

    @DynamoDbAttribute("timestamp")
    public Long getTimestamp() {
        return timestamp;
    }

    @DynamoDbAttribute("expirationTime")
    public Long getExpirationTime() {
        return expirationTime;
    }

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
