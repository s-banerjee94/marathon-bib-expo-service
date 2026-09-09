package com.timekeeper.bibexpo.inventory.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "Points an existing goody link at a different item")
public class UpdateInventoryGoodieMappingRequest {

    @NotNull(message = "Item is required")
    @Schema(description = "The inventory item this goody is handed out from", example = "12")
    private Long itemId;
}
