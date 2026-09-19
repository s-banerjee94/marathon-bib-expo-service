package com.timekeeper.bibexpo.inventory.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Teaches an item one spelling that appears in imported rosters")
public class CreateInventoryVariantAliasRequest {

    @NotBlank(message = "Value is required")
    @Size(max = 150, message = "Value must be at most 150 characters")
    @Schema(description = "The spelling as it appears in the roster, matched ignoring case and "
            + "surrounding spaces", example = "Medium")
    private String sourceValue;

    @Schema(description = "The variant of this item that spelling means. Leave it out to record "
            + "that the spelling is owed nothing at all, as a blank cell usually is",
            example = "42", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Long variantId;
}
