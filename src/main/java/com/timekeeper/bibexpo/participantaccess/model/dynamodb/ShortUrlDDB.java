package com.timekeeper.bibexpo.participantaccess.model.dynamodb;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@DynamoDbBean
public class ShortUrlDDB {

    @Getter(onMethod_ = @DynamoDbPartitionKey)
    private String shortCode;

    private String eventId;
    private String bibNumber;
    private String createdAt;
    private Long expirationTime;
}
