package com.timekeeper.bibexpo.inventory.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Adds a new location where stock can physically sit")
public class CreateInventoryLocationRequest {

    @NotBlank(message = "Location name is required")
    @Size(max = 150, message = "Location name must be at most 150 characters")
    @Schema(description = "Display name", example = "Expo Counter 1")
    private String name;

    @NotNull(message = "Location type is required")
    @Schema(description = "Location type term ID", example = "9")
    private Long typeId;
}
