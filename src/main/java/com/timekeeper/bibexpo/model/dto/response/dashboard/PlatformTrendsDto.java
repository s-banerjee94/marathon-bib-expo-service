package com.timekeeper.bibexpo.model.dto.response.dashboard;

import com.timekeeper.bibexpo.model.enums.TrendInterval;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Platform growth trend data for the chart and KPI sparklines")
public class PlatformTrendsDto {

    @Schema(description = "Bucket interval", example = "MONTH")
    private TrendInterval interval;

    @Schema(description = "Number of buckets returned", example = "12")
    private int buckets;

    @Schema(description = "Label for the start of each bucket (ISO date, or yyyy-MM for MONTH)", example = "[\"2025-07\",\"2025-08\"]")
    private List<String> bucketLabels;

    @Schema(description = "Cumulative metric values aligned to bucketLabels")
    private PlatformTrendSeriesDto series;
}
