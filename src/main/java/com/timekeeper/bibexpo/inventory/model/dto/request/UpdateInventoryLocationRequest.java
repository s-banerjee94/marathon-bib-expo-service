package com.timekeeper.bibexpo.inventory.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Renames a location or changes its type. Fields left out are unchanged")
public class UpdateInventoryLocationRequest {

    @Size(max = 150, message = "Location name must be at most 150 characters")
    @Schema(description = "New display name", example = "Expo Counter 1", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String name;

    @Schema(description = "New term ID for this location's type", example = "1", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Long typeId;
}
