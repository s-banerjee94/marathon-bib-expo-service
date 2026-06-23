package com.timekeeper.bibexpo.model.dto.response.dashboard;

import com.timekeeper.bibexpo.model.entity.Organization;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Organization profile block of the org dashboard")
public class OrgInfoDto {

    @Schema(description = "Organization ID", example = "17")
    private Long id;

    @Schema(description = "Organization name", example = "Pune Runners Co.")
    private String organizerName;

    @Schema(description = "Contact email", example = "contact@punerunners.in")
    private String email;

    @Schema(description = "Contact phone number", example = "+919876543210")
    private String phoneNumber;

    @Schema(description = "City", example = "Pune")
    private String city;

    @Schema(description = "State or province", example = "MH")
    private String stateProvince;

    @Schema(description = "Country code", example = "IN")
    private String country;

    @Schema(description = "Subscription tier; PAY_AS_YOU_GO is the baseline", example = "PREMIUM",
            allowableValues = {"PAY_AS_YOU_GO", "PREMIUM", "PARTNER"})
    private String subscriptionTier;

    @Schema(description = "Subscription status, derived from the tier (FREE on the PAY_AS_YOU_GO baseline)",
            example = "ACTIVE", allowableValues = {"ACTIVE", "EXPIRED", "FREE"})
    private String subscriptionStatus;

    @Schema(description = "Subscription term start; null on the PAY_AS_YOU_GO baseline")
    private LocalDateTime subscriptionStartDate;

    @Schema(description = "Subscription term end (when PREMIUM/PARTNER lapses and falls back to PAY_AS_YOU_GO); null on the baseline")
    private LocalDateTime subscriptionEndDate;

    @Schema(description = "Account creation timestamp")
    private Instant createdAt;

    @Schema(description = "Whether the organization is enabled", example = "true")
    private Boolean enabled;

    @Schema(description = "Short-lived presigned URL for the organization logo, null if none set",
            example = "https://bucket.s3.ap-south-1.amazonaws.com/organizations/1/logo/uuid.png?X-Amz-...")
    private String logoUrl;

    public static OrgInfoDto fromEntity(Organization org) {
        return OrgInfoDto.builder()
                .id(org.getId())
                .organizerName(org.getOrganizerName())
                .email(org.getEmail())
                .phoneNumber(org.getPhoneNumber())
                .city(org.getCity())
                .stateProvince(org.getStateProvince())
                .country(org.getCountry())
                .subscriptionTier(org.getSubscriptionTier())
                .subscriptionStatus(org.getSubscriptionStatus())
                .subscriptionStartDate(org.getSubscriptionStartDate())
                .subscriptionEndDate(org.getSubscriptionEndDate())
                .createdAt(org.getCreatedAt())
                .enabled(org.getEnabled())
                .build();
    }
}
