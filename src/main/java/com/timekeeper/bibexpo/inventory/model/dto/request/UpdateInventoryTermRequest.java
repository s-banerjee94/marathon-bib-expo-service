package com.timekeeper.bibexpo.inventory.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Renames an existing term")
public class UpdateInventoryTermRequest {

    @NotBlank(message = "Term name is required")
    @Size(max = 100, message = "Term name must be at most 100 characters")
    @Schema(description = "New display name", example = "Apparel")
    private String name;
}
