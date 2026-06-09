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
@Schema(description = "Billed totals broken into gross, net (taxable value) and GST")
public class BilledStat {

    @Schema(description = "Gross billed (net + tax)", example = "4820000.00")
    private BigDecimal amount;

    @Schema(description = "Net billed (taxable value, before GST)", example = "4084745.76")
    private BigDecimal net;

    @Schema(description = "GST billed", example = "735254.24")
    private BigDecimal tax;

    @Schema(description = "Number of bills", example = "312")
    private long count;
}
