package com.timekeeper.bibexpo.model.dto.response.stats;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Organization statistics breakdown (GLOBAL scope only)")
public class OrganizationStatsData {

    @Schema(description = "Total non-deleted organizations", example = "25")
    private long total;

    @Schema(description = "Active (enabled) organizations", example = "20")
    private long active;

    @Schema(description = "Inactive (disabled) organizations", example = "5")
    private long inactive;

    @Schema(description = "Organization count per subscription tier")
    private Map<String, Long> bySubscriptionTier;

    @Schema(description = "Organization count per subscription status")
    private Map<String, Long> bySubscriptionStatus;
}
