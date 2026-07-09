package com.timekeeper.bibexpo.model.dto.response.dashboard;

import com.timekeeper.bibexpo.model.enums.DashboardRange;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Platform revenue summary — collected (paid) money within the range plus monthly bars")
public class PlatformRevenueResponse {

    @Schema(description = "When this response was computed", example = "2026-06-23T11:48:02Z")
    private Instant refreshedAt;

    @Schema(description = "Currency of all amounts", example = "INR")
    private String currency;

    @Schema(description = "The range window these figures cover", example = "ALL")
    private DashboardRange range;

    @Schema(description = "Total collected (paid) within range, in currency major units", example = "284000")
    private BigDecimal earned;

    @Schema(description = "Signed % change vs the previous equal-length period; null when no prior period (range=ALL or empty prior)",
            example = "6.2", nullable = true)
    private BigDecimal deltaPct;

    @Schema(description = "Caption for the delta chip; null on range=ALL", example = "vs previous 12 months", nullable = true)
    private String comparisonLabel;

    @Schema(description = "Collected per bucket for the bars")
    private PlatformRevenueTrendDto trend;
}
