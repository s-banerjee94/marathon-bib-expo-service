package com.timekeeper.bibexpo.inventory.model.dto.request;

import com.timekeeper.bibexpo.inventory.model.enums.AttributeType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Adds a new reusable attribute")
public class CreateInventoryAttributeRequest {

    @NotBlank(message = "Attribute name is required")
    @Size(max = 100, message = "Attribute name must be at most 100 characters")
    @Schema(description = "Display name", example = "Size")
    private String name;

    @NotNull(message = "Attribute type is required")
    @Schema(description = "The kind of value this attribute holds", example = "SELECT")
    private AttributeType type;

    @NotNull(message = "variantAttribute must be set explicitly")
    @Schema(description = "True if choosing a value splits stock into a separate variant; "
            + "false if the value is the same for the whole item", example = "true")
    private Boolean variantAttribute;

    @NotNull(message = "required must be set explicitly")
    @Schema(description = "Whether an item using this attribute must supply a value", example = "true")
    private Boolean required;
}
