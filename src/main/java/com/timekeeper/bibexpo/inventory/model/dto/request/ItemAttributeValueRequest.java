package com.timekeeper.bibexpo.inventory.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "One attribute's value — optionId for a SELECT attribute, rawValue for any other type")
public class ItemAttributeValueRequest {

    @NotNull(message = "Attribute is required")
    @Schema(description = "Attribute ID", example = "1")
    private Long attributeId;

    @Schema(description = "Chosen value ID, for a SELECT attribute", example = "1",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Long optionId;

    @Size(max = 255, message = "Value must be at most 255 characters")
    @Schema(description = "Value, for a TEXT/NUMBER/BOOLEAN attribute", example = "true",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String rawValue;
}
