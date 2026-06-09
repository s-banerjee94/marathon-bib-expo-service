package com.timekeeper.bibexpo.billing.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = """
        Update to the system-generated PARTICIPANT fee line on a draft bill. Send only the field(s) \
        you want to change — an omitted field keeps its current value. The line's type and description \
        are system-managed and cannot be changed; the line amount is recomputed as quantity × unitPrice.""")
public class ParticipantLineRequest {

    @Positive
    @Schema(description = "New per-participant unit price; omit to keep the current price.", example = "10.00")
    private BigDecimal unitPrice;

    @Positive
    @Schema(description = "New participant quantity; omit to keep the current count. Note: a later count "
            + "refresh from the billing pipeline overwrites a manual quantity.", example = "1200")
    private Integer quantity;
}
