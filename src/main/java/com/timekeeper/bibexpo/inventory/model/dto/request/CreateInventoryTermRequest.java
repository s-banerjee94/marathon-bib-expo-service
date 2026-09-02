package com.timekeeper.bibexpo.inventory.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Adds a new term to a vocabulary — the kind it belongs to comes from the query string")
public class CreateInventoryTermRequest {

    @NotBlank(message = "Term name is required")
    @Size(max = 100, message = "Term name must be at most 100 characters")
    @Schema(description = "Display name", example = "Apparel")
    private String name;
}
