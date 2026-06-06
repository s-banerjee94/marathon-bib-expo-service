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
@Schema(description = "Billed amount and bill count for one trigger reason")
public class ReasonAggregate {

    @Schema(description = "Sum of totalAmount", example = "3950000.00")
    private BigDecimal amount;

    @Schema(description = "Number of bills", example = "256")
    private long count;
}
