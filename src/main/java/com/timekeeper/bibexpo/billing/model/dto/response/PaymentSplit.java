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
@Schema(description = "Paid vs unpaid gross amounts for the payment-distribution donut")
public class PaymentSplit {

    @Schema(description = "Gross amount paid (equals collected.amount)", example = "4124560.00")
    private BigDecimal paid;

    @Schema(description = "Gross amount unpaid (equals outstanding.amount)", example = "1123240.00")
    private BigDecimal unpaid;
}
