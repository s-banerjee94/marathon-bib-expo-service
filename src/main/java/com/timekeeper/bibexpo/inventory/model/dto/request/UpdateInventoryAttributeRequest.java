package com.timekeeper.bibexpo.inventory.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Renames an attribute. type and variantAttribute cannot be changed after creation.")
public class UpdateInventoryAttributeRequest {

    @Size(max = 100, message = "Attribute name must be at most 100 characters")
    @Schema(description = "New display name", example = "Size", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String name;
}
