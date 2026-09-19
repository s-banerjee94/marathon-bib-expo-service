package com.timekeeper.bibexpo.inventory.model.dto.request;

import com.timekeeper.bibexpo.inventory.model.enums.MovementReason;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import lombok.Data;

@Data
@Schema(description = "Corrects the counted quantity at a location. DAMAGED and LOST always remove stock; CORRECTION adds or removes according to the sign.")
public class AdjustStockRequest {

    @NotNull
    @Schema(description = "Variant being adjusted", example = "1")
    private Long variantId;

    @NotNull
    @Schema(description = "Location being adjusted", example = "1")
    private Long locationId;

    @NotNull
    @Schema(description = "Change in quantity, never zero. With CORRECTION the sign decides the direction — negative removes, "
            + "positive adds. With DAMAGED or LOST the sign is ignored and this many units are always removed.", example = "-5")
    private Integer quantity;

    @NotNull
    @Schema(description = "Why the count changed — DAMAGED, LOST or CORRECTION", example = "DAMAGED")
    private MovementReason reason;
}
