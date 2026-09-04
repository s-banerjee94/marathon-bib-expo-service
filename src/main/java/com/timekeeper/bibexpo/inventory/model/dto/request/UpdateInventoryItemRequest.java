package com.timekeeper.bibexpo.inventory.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Updates an item. Fields left out are unchanged")
public class UpdateInventoryItemRequest {

    @Size(max = 150, message = "Item name must be at most 150 characters")
    @Schema(description = "New display name", example = "Race Tee", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String name;

    @Schema(description = "New term ID for this item's category", example = "1", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Long categoryId;

    @Schema(description = "New term ID for this item's unit", example = "1", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Long unitId;

    @Min(value = 0, message = "Low stock threshold cannot be negative")
    @Schema(description = "New balance at or below which the item is considered low on stock", example = "50",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Integer lowStockThreshold;
}
