package com.timekeeper.bibexpo.billing.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Billed-vs-collected over the last 12 months; all arrays are index-aligned with bucketLabels, oldest first")
public class BillStatsTrend {

    @Schema(description = "Bucket width (always MONTH)", example = "MONTH")
    private String interval;

    @Schema(description = "Month labels (yyyy-MM), oldest first", example = "[\"2025-07\", \"2026-06\"]")
    private List<String> bucketLabels;

    @Schema(description = "Gross billed per month, by finalize date", example = "[320000, 410000]")
    private List<BigDecimal> billed;

    @Schema(description = "Gross collected per month, by payment date", example = "[210000, 350000]")
    private List<BigDecimal> collected;

    @Schema(description = "Bills finalized per month", example = "[22, 28]")
    private List<Long> count;
}
