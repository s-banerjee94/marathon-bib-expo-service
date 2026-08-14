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

    private String updatedAt;

    // The statKey vocabulary. It sits on the record carrying the field because the writer and the
    // readers live in different modules: whoever holds the constants would otherwise be imported
    // by the others purely to spell a key.
    public static final String KEY_TOTAL = "TOTAL";
    public static final String KEY_BIB_COLLECTED = "BIB_COLLECTED";
    public static final String PREFIX_RACE = "RACE#";
    public static final String PREFIX_CATEGORY = "CATEGORY#";
    public static final String PREFIX_GENDER = "GENDER#";
    public static final String PREFIX_GOODIE = "GOODIE#";
    public static final String PREFIX_HOUR = "HOUR#";
    public static final String PREFIX_DIST = "DIST#";
    public static final String SUFFIX_COLLECTED = "#COLLECTED";
    public static final String SUFFIX_DISTRIBUTED = "#DISTRIBUTED";
    public static final String GENDER_M = PREFIX_GENDER + "M";
    public static final String GENDER_F = PREFIX_GENDER + "F";
    public static final String GENDER_O = PREFIX_GENDER + "O";
}
