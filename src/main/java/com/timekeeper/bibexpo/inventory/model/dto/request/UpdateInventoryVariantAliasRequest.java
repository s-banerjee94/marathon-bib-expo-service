package com.timekeeper.bibexpo.inventory.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Points an existing spelling at a different variant")
public class UpdateInventoryVariantAliasRequest {

    @Schema(description = "The variant of this item that spelling means. Send null to record that "
            + "the spelling is owed nothing at all", example = "42",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Long variantId;
}
