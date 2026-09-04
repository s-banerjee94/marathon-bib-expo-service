package com.timekeeper.bibexpo.inventory.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Renames an existing attribute value")
public class UpdateInventoryAttributeOptionRequest {

    @NotBlank(message = "Value is required")
    @Size(max = 100, message = "Value must be at most 100 characters")
    @Schema(description = "New value", example = "500ml")
    private String value;
}
