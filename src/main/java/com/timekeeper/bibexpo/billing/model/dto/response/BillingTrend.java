package com.timekeeper.bibexpo.billing.model.dto.response;

import com.timekeeper.bibexpo.model.enums.TrendInterval;
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
@Schema(description = "Billed amount and bill count per time bucket, oldest bucket first")
public class BillingTrend {

    @Schema(description = "Bucket width", example = "MONTH")
    private TrendInterval interval;

    @Schema(description = "Bucket labels aligned with the data arrays", example = "[\"2025-07\", \"2025-08\"]")
    private List<String> bucketLabels;

    @Schema(description = "Billed amount per bucket", example = "[320000, 410000]")
    private List<BigDecimal> billed;

    @Schema(description = "Bill count per bucket", example = "[22, 28]")
    private List<Long> count;
}
