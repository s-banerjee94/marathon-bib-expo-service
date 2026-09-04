package com.timekeeper.bibexpo.inventory.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "Adds a variant to an existing item")
public class CreateInventoryVariantRequest {

    @Schema(description = "Values for this item's variant-defining attributes, e.g. Size=L, Colour=Blue; "
            + "omit for a plain code-only variant", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private List<@Valid ItemAttributeValueRequest> attributeValues;
}
