package com.timekeeper.bibexpo.inventory.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Renames an attribute or changes whether it is required. "
        + "Fields left out are unchanged. type and variantAttribute cannot be changed after creation.")
public class UpdateInventoryAttributeRequest {

    @Size(max = 100, message = "Attribute name must be at most 100 characters")
    @Schema(description = "New display name", example = "Size", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String name;

    @Schema(description = "New required flag", example = "true", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Boolean required;
}
