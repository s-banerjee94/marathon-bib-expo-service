package com.timekeeper.bibexpo.inventory.model.dto.request;

import com.timekeeper.bibexpo.inventory.model.enums.MovementReason;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = """
        One line of a posting. RECEIPT: variantId, toLocationId, quantity. TRANSFER: variantId, \
        fromLocationId, toLocationId, quantity. ADJUSTMENT count: variantId, locationId, reason CORRECTION, \
        countedOnHand, expectedOnHand. ADJUSTMENT damaged or lost: variantId, locationId, reason DAMAGED or \
        LOST, quantity.""")
public class StockPostingLineRequest {

    @NotNull(message = "Variant is required")
    @Schema(description = "Variant this line moves", example = "11")
    private Long variantId;

    @Schema(description = "TRANSFER only: where the stock leaves", example = "3", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Long fromLocationId;

    @Schema(description = "RECEIPT and TRANSFER: where the stock arrives", example = "2", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Long toLocationId;

    @Schema(description = "ADJUSTMENT only: the shelf being counted or written off", example = "2",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Long locationId;

    @Schema(description = "ADJUSTMENT only: CORRECTION for a count, DAMAGED or LOST for a write-off", example = "CORRECTION",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private MovementReason reason;

    @Min(value = 1, message = "Quantity must be at least 1")
    @Max(value = 100_000, message = "Quantity must be at most 100,000")
    @Schema(description = "Units moved, 1 to 100,000. Not used by a count.", example = "40",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Integer quantity;

    @Min(value = 0, message = "Counted quantity cannot be negative")
    @Max(value = 1_000_000, message = "Counted quantity must be at most 1,000,000")
    @Schema(description = "Count only: what is on the shelf now", example = "35", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Integer countedOnHand;

    @Schema(description = "Count only: the balance the screen showed when counting started; a different balance now answers 409",
            example = "40", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Integer expectedOnHand;
}
