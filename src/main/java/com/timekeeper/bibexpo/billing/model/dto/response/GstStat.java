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
@Schema(description = "GST liability split by whether the bill it sits on has been collected")
public class GstStat {

    @Schema(description = "GST on collected (paid) bills", example = "474000.00")
    private BigDecimal collected;

    @Schema(description = "GST on outstanding (unpaid) bills", example = "261254.24")
    private BigDecimal outstanding;

    @Schema(description = "Total GST billed (collected + outstanding)", example = "735254.24")
    private BigDecimal total;
}
