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
public class EventStatsDDB {

    @Getter(onMethod_ = @DynamoDbPartitionKey)
    private String eventId;

    @Getter(onMethod_ = @DynamoDbSortKey)
    private String statKey;

    private Long count;

    private String raceName;

    private String categoryName;

    private String updatedAt;
}
