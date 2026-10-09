package com.timekeeper.bibexpo.inventory.model.dto.request;

import com.timekeeper.bibexpo.inventory.model.enums.MovementReason;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import lombok.Data;

@Data
@Schema(description = "Corrects the counted quantity at a location. DAMAGED and LOST always remove stock; CORRECTION adds or removes according to the sign.")
public class AdjustStockRequest {

    @NotNull(message = "Variant is required")
    @Schema(description = "Variant being adjusted", example = "1")
    private Long variantId;

    @NotNull(message = "Location is required")
    @Schema(description = "Location being adjusted", example = "1")
    private Long locationId;

    @NotNull(message = "Quantity is required")
    @Min(value = -1_000_000, message = "Quantity must be between -1,000,000 and 1,000,000")
    @Max(value = 1_000_000, message = "Quantity must be between -1,000,000 and 1,000,000")
    @Schema(description = "Change in quantity, never zero and at most 1,000,000 either way. With CORRECTION the sign decides the direction — negative removes, "
            + "positive adds. With DAMAGED or LOST the sign is ignored and this many units are always removed.", example = "-5")
    private Integer quantity;

    @NotNull(message = "Reason is required")
    @Schema(description = "Why the count changed — DAMAGED, LOST or CORRECTION", example = "DAMAGED")
    private MovementReason reason;
}
