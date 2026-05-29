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
@Schema(description = "Trend sparkline data for the org dashboard")
public class TrendsDto {

    @Schema(description = "Bucket interval", example = "WEEK")
    private TrendInterval interval;

    @Schema(description = "Number of buckets returned", example = "7")
    private int buckets;

    @Schema(description = "ISO date label for the start of each bucket", example = "[\"2026-04-13\",\"2026-04-20\"]")
    private List<String> bucketLabels;

    @Schema(description = "Cumulative metric values aligned to bucketLabels")
    private TrendSeriesDto series;
}
