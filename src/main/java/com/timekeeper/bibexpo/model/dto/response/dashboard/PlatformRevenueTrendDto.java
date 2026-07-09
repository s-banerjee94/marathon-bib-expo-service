package com.timekeeper.bibexpo.model.dto.response.dashboard;

import com.timekeeper.bibexpo.model.enums.TrendInterval;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Revenue collected per bucket for the monthly bars, oldest first")
public class PlatformRevenueTrendDto {

    @Schema(description = "Bucket interval", example = "MONTH")
    private TrendInterval interval;

    @Schema(description = "Label for the start of each bucket (yyyy-MM for MONTH, ISO date otherwise)",
            example = "[\"2025-10\",\"2025-11\"]")
    private List<String> bucketLabels;

    @Schema(description = "Collected amount per bucket, index-aligned with bucketLabels", example = "[170000, 190000]")
    private List<BigDecimal> earned;
}
