package com.timekeeper.bibexpo.inventory.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Moves stock from one location to another. A deliberate act — blocked, never allowed to go negative.")
public class TransferStockRequest {

    @NotNull
    @Schema(description = "Variant being moved", example = "1")
    private Long variantId;

    @NotNull
    @Schema(description = "Location the stock is leaving", example = "1")
    private Long fromLocationId;

    @NotNull
    @Schema(description = "Location the stock is arriving at", example = "2")
    private Long toLocationId;

    @NotNull
    @Positive
    @Schema(description = "Quantity to move", example = "900")
    private Integer quantity;

    @Size(max = 100)
    @Schema(description = "Optional note for this transfer", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String reference;
}
