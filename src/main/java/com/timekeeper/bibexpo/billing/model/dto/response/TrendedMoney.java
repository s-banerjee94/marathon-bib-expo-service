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
@Schema(description = "A money total with its change vs the previous period and a short sparkline series")
public class TrendedMoney {

    @Schema(description = "The amount in the current window", example = "5247800.00")
    private BigDecimal amount;

    @Schema(description = "Signed % change vs the previous equal-length period; null when there is no prior period or not applicable",
            example = "13.2", nullable = true)
    private BigDecimal deltaPct;

    @Schema(description = "Bill count for this amount; only set on billedThisMonth", example = "158", nullable = true)
    private Long count;

    @Schema(description = "Short cumulative series (~6 points, oldest first) ending at the current amount, for the card mini-chart",
            example = "[3280000, 3620000, 4010000, 4380000, 4710000, 5247800]")
    private List<BigDecimal> spark;
}
