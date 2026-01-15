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
@NoArgsConstructor
@AllArgsConstructor
@Builder
@DynamoDbBean
public class DistributionLogDDB {

    @Getter(onMethod_ = @DynamoDbPartitionKey)
    private String eventId;

    @Getter(onMethod_ = @DynamoDbSortKey)
    private String timestamp;

    @Getter(onMethod_ = @DynamoDbSecondarySortKey(indexNames = "LSI-BibNumberIndex"))
    private String bibNumber;

    @Getter(onMethod_ = @DynamoDbSecondarySortKey(indexNames = "LSI-ActionIndex"))
    private String action;

    @Getter(onMethod_ = @DynamoDbSecondarySortKey(indexNames = "LSI-ItemNameIndex"))
    private String itemName;

    @Getter(onMethod_ = @DynamoDbSecondarySortKey(indexNames = "LSI-PerformedByIndex"))
    private String performedBy;

    @Getter(onMethod_ = @DynamoDbSecondarySortKey(indexNames = "LSI-CollectorNameIndex"))
    private String collectorName;

    private String collectorPhone;
    private String details;
}
