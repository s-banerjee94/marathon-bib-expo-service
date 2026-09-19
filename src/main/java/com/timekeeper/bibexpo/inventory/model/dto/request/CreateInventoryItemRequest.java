package com.timekeeper.bibexpo.inventory.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "Adds a new item — a thing the organization stocks")
public class CreateInventoryItemRequest {

    @NotBlank(message = "Item name is required")
    @Size(max = 150, message = "Item name must be at most 150 characters")
    @Schema(description = "Display name", example = "Race Tee")
    private String name;

    @NotNull(message = "Category is required")
    @Schema(description = "Category term ID", example = "4")
    private Long categoryId;

    @NotNull(message = "Unit is required")
    @Schema(description = "Unit term ID", example = "1")
    private Long unitId;

    @Min(value = 0, message = "Low stock threshold cannot be negative")
    @Schema(description = "Balance at or below which the item is considered low on stock", example = "50",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Integer lowStockThreshold;

    @Size(max = 500, message = "Note must be at most 500 characters")
    @Schema(description = "A short note for whoever works with this item next — a reminder or a warning, "
            + "such as reserved for the sponsor lounge. Describe the product itself with an attribute instead",
            example = "Reserved for the sponsor lounge — do not hand out at the counter.",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String note;

    @Schema(description = "Values for this item's non-variant-defining attributes, applied once to the whole item "
            + "(e.g. Recyclable=true); omit if the item uses none", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private List<@Valid ItemAttributeValueRequest> attributes;

    @Schema(description = "Variants to create with this item; omit for a single DEFAULT variant with no attributes",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private List<@Valid CreateInventoryVariantRequest> variants;
}
