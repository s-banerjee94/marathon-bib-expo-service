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

import java.util.HashMap;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@DynamoDbBean
public class ParticipantDDB {

    @Getter(onMethod_ = @DynamoDbPartitionKey)
    private String eventId;

    @Getter(onMethod_ = @DynamoDbSortKey)
    private String bibNumber;

    private String chipNumber;

    @Getter(onMethod_ = @DynamoDbSecondarySortKey(indexNames = "LSI-FullNameIndex"))
    private String fullName;

    @Getter(onMethod_ = @DynamoDbSecondarySortKey(indexNames = "LSI-EmailIndex"))
    private String email;

    @Getter(onMethod_ = @DynamoDbSecondarySortKey(indexNames = "LSI-PhoneNumberIndex"))
    private String phoneNumber;
    private String dateOfBirth;
    private Integer age;
    private String gender;
    private String country;
    private String city;

    private String raceId;

    @Getter(onMethod_ = @DynamoDbSecondarySortKey(indexNames = "LSI-RaceNameIndex"))
    private String raceName;

    private String categoryId;

    @Getter(onMethod_ = @DynamoDbSecondarySortKey(indexNames = "LSI-CategoryNameIndex"))
    private String categoryName;
    private String bibCollectedAt;
    private String bibCollectedByName;
    private String bibCollectedByPhone;
    private String bibDistributedBy;

    @Builder.Default
    private Map<String, String> smsCampaignSends = new HashMap<>();

    @Builder.Default
    private Map<String, String> goodies = new HashMap<>();

    @Builder.Default
    private Map<String, String> goodiesDistribution = new HashMap<>();

    private String emergencyContactName;
    private String emergencyContactPhone;
    private String notes;
    private String createdAt;
    private String createdBy;
    private String updatedAt;
    private String updatedBy;
}
