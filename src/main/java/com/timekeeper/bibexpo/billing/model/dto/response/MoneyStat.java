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
@Schema(description = "A money amount and the number of bills it covers")
public class MoneyStat {

    @Schema(description = "Sum of bill totals (gross, GST-inclusive)", example = "3110000.00")
    private BigDecimal amount;

    @Schema(description = "Number of bills", example = "201")
    private long count;
}
