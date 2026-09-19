package com.timekeeper.bibexpo.inventory.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "Points an existing goody link at a different item or location")
public class UpdateInventoryGoodieMappingRequest {

    @NotNull(message = "Item is required")
    @Schema(description = "The inventory item this goody is handed out from", example = "12")
    private Long itemId;

    @Schema(description = "The location it is handed out from, which is where a handover deducts "
            + "stock; omitting it clears the location, which a published event does not allow",
            example = "4", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Long locationId;
}
