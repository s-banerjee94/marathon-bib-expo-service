package com.timekeeper.bibexpo.inventory.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Moves stock from one location to another. A deliberate act — blocked, never allowed to go negative.")
public class TransferStockRequest {

    @NotNull(message = "Variant is required")
    @Schema(description = "Variant being moved", example = "1")
    private Long variantId;

    @NotNull(message = "Source location is required")
    @Schema(description = "Location the stock is leaving", example = "1")
    private Long fromLocationId;

    @NotNull(message = "Destination location is required")
    @Schema(description = "Location the stock is arriving at", example = "2")
    private Long toLocationId;

    @NotNull(message = "Quantity is required")
    @Positive(message = "Quantity must be at least 1")
    @Max(value = 1_000_000, message = "Quantity must be at most 1,000,000")
    @Schema(description = "Quantity to move", example = "900")
    private Integer quantity;

    @Size(max = 100, message = "Reference must be at most 100 characters")
    @Schema(description = "Optional note for this transfer", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String reference;
}
