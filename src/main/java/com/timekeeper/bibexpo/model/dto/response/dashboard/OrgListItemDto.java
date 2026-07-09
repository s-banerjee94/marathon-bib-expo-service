package com.timekeeper.bibexpo.model.dto.response.dashboard;

import com.timekeeper.bibexpo.model.entity.Organization;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Lightweight organization entry for the Recent Organizations list")
public class OrgListItemDto {

    @Schema(description = "Organization ID", example = "17")
    private Long id;

    @Schema(description = "Organizer name", example = "Coastal Runners")
    private String organizerName;

    @Schema(description = "Subscription tier; PAY_AS_YOU_GO is the baseline", example = "PREMIUM",
            allowableValues = {"PAY_AS_YOU_GO", "PREMIUM", "PARTNER"}, nullable = true)
    private String subscriptionTier;

    @Schema(description = "Subscription status, derived from the tier (FREE on the PAY_AS_YOU_GO baseline)",
            example = "ACTIVE", allowableValues = {"ACTIVE", "EXPIRED", "FREE"}, nullable = true)
    private String subscriptionStatus;

    @Schema(description = "City", example = "Chennai", nullable = true)
    private String city;

    @Schema(description = "State / province", example = "TN", nullable = true)
    private String stateProvince;

    @Schema(description = "When the organization was created", example = "2026-06-20T09:15:00Z")
    private Instant createdAt;

    public static OrgListItemDto fromEntity(Organization org) {
        return OrgListItemDto.builder()
                .id(org.getId())
                .organizerName(org.getOrganizerName())
                .subscriptionTier(org.getSubscriptionTier())
                .subscriptionStatus(org.getSubscriptionStatus())
                .city(org.getCity())
                .stateProvince(org.getStateProvince())
                .createdAt(org.getCreatedAt())
                .build();
    }
}
