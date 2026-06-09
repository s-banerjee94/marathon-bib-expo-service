package com.timekeeper.bibexpo.billing.model.dto.response;

import com.timekeeper.bibexpo.billing.model.entity.InvoiceLineItem;
import com.timekeeper.bibexpo.billing.model.entity.LineItemKind;
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
@Schema(description = "One line on a bill. The PARTICIPANT line is the participant fee; the rest are "
        + "extra charges or adjustments. Every line's amount feeds the bill subtotal.")
public class LineItemResponse {

    @Schema(description = "Line item id (use this in the update/delete line-item endpoints)", example = "12")
    private Long id;

    @Schema(description = "Line type. PARTICIPANT = the participant fee (system-generated, one per bill); "
            + "EXTRA_USER / SMS_CAMPAIGN / SURCHARGE / CUSTOM = charges; DISCOUNT = a reduction.",
            example = "DISCOUNT")
    private LineItemKind kind;

    @Schema(description = "Human-readable label shown on the bill", example = "Festive discount")
    private String description;

    @Schema(description = "Quantity for a charge/participant line (amount = quantity × unitPrice); null on a discount",
            example = "1")
    private Integer quantity;

    @Schema(description = "Per-unit price for a charge/participant line; null on a discount", example = "100.00")
    private BigDecimal unitPrice;

    @Schema(description = "Signed contribution to the subtotal — quantity × unitPrice for a charge, negative for a discount",
            example = "-500.00")
    private BigDecimal amount;

    @Schema(description = "For a percentage discount, the percent applied to the pre-tax charge base "
            + "(amount is derived from it); null for fixed-amount discounts and all other lines", example = "10.00")
    private BigDecimal discountPercent;

    @Schema(description = "True if produced by the billing pipeline (the PARTICIPANT line); user-added lines are false",
            example = "false")
    private boolean systemGenerated;

    public static LineItemResponse fromEntity(InvoiceLineItem item) {
        return LineItemResponse.builder()
                .id(item.getId())
                .kind(item.getKind())
                .description(item.getDescription())
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .amount(item.getAmount())
                .discountPercent(item.getDiscountPercent())
                .systemGenerated(item.isSystemGenerated())
                .build();
    }
}
