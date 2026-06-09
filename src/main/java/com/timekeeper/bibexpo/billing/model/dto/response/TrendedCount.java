package com.timekeeper.bibexpo.billing.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "A headline count with its percentage change vs the previous comparison period")
public class TrendedCount {

    @Schema(description = "The count in the current window", example = "826")
    private long value;

    @Schema(description = "Signed % change vs the previous equal-length period; null when there is no prior period",
            example = "11.7", nullable = true)
    private BigDecimal deltaPct;
}
