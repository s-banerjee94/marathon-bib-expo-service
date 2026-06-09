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
@Schema(description = "One receivables-aging band — outstanding bills grouped by how long they have been unpaid")
public class AgingBucket {

    @Schema(description = "Age band in days since the bill was finalized", example = "0-30",
            allowableValues = {"0-30", "31-60", "61-90", "90+"})
    private String bucket;

    @Schema(description = "Outstanding amount in this band", example = "900000.00")
    private BigDecimal amount;

    @Schema(description = "Number of outstanding bills in this band", example = "60")
    private long count;
}
