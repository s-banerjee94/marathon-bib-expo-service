package com.timekeeper.bibexpo.model.dto.response;

import com.timekeeper.bibexpo.model.dto.response.stats.OrganizationStatsData;
import com.timekeeper.bibexpo.model.enums.StatisticsScope;
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
@Schema(description = "Global organization statistics snapshot — accessible by ROOT and ADMIN only")
public class OrganizationStatisticsResponse {

    @Schema(description = "Always GLOBAL for this endpoint", example = "GLOBAL")
    private StatisticsScope scope;

    @Schema(description = "When this snapshot was last refreshed", example = "2026-02-23T10:00:00Z")
    private Instant refreshedAt;

    @Schema(description = "Organization statistics breakdown")
    private OrganizationStatsData organizations;
}
