package com.timekeeper.bibexpo.billing.model.dto.request;

import com.timekeeper.bibexpo.billing.model.entity.LineItemKind;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = """
        A line to add to or update on a draft bill. The required fields depend on kind:
        • Charge (EXTRA_USER, SMS_CAMPAIGN, SURCHARGE, CUSTOM): kind, description, unitPrice, and \
        optional quantity (defaults to 1). The amount is computed as quantity × unitPrice — do not send amount.
        • Discount (DISCOUNT): kind, description, and exactly one of amount (fixed) or percent.
        • Updating the PARTICIPANT line: send unitPrice and/or quantity (its kind stays PARTICIPANT).
        PARTICIPANT cannot be used when adding a new line.""")
public class LineItemRequest {

    @NotNull(message = "Line type is required")
    @Schema(description = "Line type. Use EXTRA_USER / SMS_CAMPAIGN / SURCHARGE / CUSTOM for charges, "
            + "DISCOUNT for a reduction. PARTICIPANT is only valid when updating the existing participant line.",
            example = "EXTRA_USER")
    private LineItemKind kind;

    @NotBlank(message = "Description is required")
    @Size(max = 255, message = "Description must be at most 255 characters")
    @Schema(description = "Human-readable label shown on the bill", example = "Extra distributor seats")
    private String description;

    @Positive(message = "Quantity must be at least 1")
    @Max(value = 1_000_000, message = "Quantity must be at most 1,000,000")
    @Schema(description = "Quantity for a charge line (defaults to 1); the line amount is quantity × unitPrice. "
            + "Ignored for discounts.", example = "2")
    private Integer quantity;

    @Positive(message = "Unit price must be more than zero")
    @Digits(integer = 9, fraction = 2, message = "Unit price must be under 1,000,000,000 with at most 2 decimals")
    @Schema(description = "Per-unit price for a charge line (required for charges); the line amount is "
            + "quantity × unitPrice. Ignored for discounts.", example = "200.00")
    private BigDecimal unitPrice;

    @Positive(message = "Amount must be more than zero")
    @Digits(integer = 9, fraction = 2, message = "Amount must be under 1,000,000,000 with at most 2 decimals")
    @Schema(description = "Amount for a fixed-amount DISCOUNT only (stored negative automatically). "
            + "Not used for charges (amount = quantity × unitPrice) or percentage discounts.",
            example = "100.00")
    private BigDecimal amount;

    @DecimalMin(value = "0.01", message = "Percent must be between 0.01 and 100")
    @DecimalMax(value = "100.00", message = "Percent must be between 0.01 and 100")
    @Schema(description = "Percentage for a DISCOUNT applied to the pre-tax charge base; provide this "
            + "OR amount on a discount, not both. Ignored for other kinds.",
            example = "10.00")
    private BigDecimal percent;
}
