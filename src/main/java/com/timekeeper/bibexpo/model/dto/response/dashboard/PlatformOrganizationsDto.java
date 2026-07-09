package com.timekeeper.bibexpo.model.dto.response.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Organizations block of the platform dashboard")
public class PlatformOrganizationsDto {

    @Schema(description = "Total organizations created within the selected range", example = "48")
    private long total;

    @Schema(description = "Counts grouped by subscription tier within tierRange; keys are PAY_AS_YOU_GO (baseline), PREMIUM, PARTNER (data-driven, only present tiers appear; legacy untouched rows may still show UNKNOWN)")
    private Map<String, Long> byTier;

    @Schema(description = "Counts grouped by subscription status within statusRange; keys are ACTIVE, EXPIRED, FREE (data-driven, only present statuses appear)")
    private Map<String, Long> byStatus;

    @Schema(description = "The 5 most recently created organizations, newest first")
    private List<OrgListItemDto> recent;

    @Schema(description = "Top organizations ranked by event activity, descending")
    private List<TopOrgDto> top;
}
