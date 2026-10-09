package com.timekeeper.bibexpo.billing.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = """
        Update to the system-generated PARTICIPANT fee line on a draft bill. Send only the field(s) \
        you want to change — an omitted field keeps its current value. The line's type and description \
        are system-managed and cannot be changed; the line amount is recomputed as quantity × unitPrice.""")
public class ParticipantLineRequest {

    @Positive(message = "Unit price must be more than zero")
    @Digits(integer = 9, fraction = 2, message = "Unit price must be under 1,000,000,000 with at most 2 decimals")
    @Schema(description = "New per-participant unit price; omit to keep the current price.", example = "10.00")
    private BigDecimal unitPrice;

    @Positive(message = "Quantity must be at least 1")
    @Max(value = 1_000_000, message = "Quantity must be at most 1,000,000")
    @Schema(description = "New participant quantity; omit to keep the current count. Note: a later count "
            + "refresh from the billing pipeline overwrites a manual quantity.", example = "1200")
    private Integer quantity;
}
