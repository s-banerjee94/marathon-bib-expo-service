package com.timekeeper.bibexpo.inventory.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Records stock arriving at a location from outside the system — an opening balance or a manual receipt.")
public class ReceiveStockRequest {

    @NotNull
    @Schema(description = "Variant receiving stock", example = "1")
    private Long variantId;

    @NotNull
    @Schema(description = "Location the stock is arriving at", example = "1")
    private Long locationId;

    @NotNull
    @Positive
    @Schema(description = "Quantity received", example = "2200")
    private Integer quantity;

    @Size(max = 100)
    @Schema(description = "What caused this receipt — a PO id, or a free-text note for an opening balance",
            example = "Opening balance", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String reference;
}
