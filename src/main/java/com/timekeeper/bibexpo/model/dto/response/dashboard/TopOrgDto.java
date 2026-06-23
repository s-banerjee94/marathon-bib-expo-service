package com.timekeeper.bibexpo.model.dto.response.dashboard;

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
@Schema(description = "Organization entry for the Top Organizations table, ranked by activity")
public class TopOrgDto {

    @Schema(description = "Organization ID", example = "17")
    private Long id;

    @Schema(description = "Organizer name", example = "Pune Runners Co.")
    private String organizerName;

    @Schema(description = "Subscription tier; PAY_AS_YOU_GO is the baseline", example = "PREMIUM",
            allowableValues = {"PAY_AS_YOU_GO", "PREMIUM", "PARTNER"}, nullable = true)
    private String subscriptionTier;

    @Schema(description = "Subscription status, derived from the tier (FREE on the PAY_AS_YOU_GO baseline)",
            example = "ACTIVE", allowableValues = {"ACTIVE", "EXPIRED", "FREE"}, nullable = true)
    private String subscriptionStatus;

    @Schema(description = "Total events owned by this organization", example = "38")
    private long eventCount;

    @Schema(description = "Total users belonging to this organization", example = "142")
    private long userCount;

    @Schema(description = "When the organization was created", example = "2024-03-01T00:00:00Z")
    private Instant createdAt;
}
