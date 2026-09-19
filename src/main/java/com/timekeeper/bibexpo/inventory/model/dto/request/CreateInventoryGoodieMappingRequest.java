package com.timekeeper.bibexpo.inventory.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Links one of an event's goodies columns to the item it is handed out from")
public class CreateInventoryGoodieMappingRequest {

    @NotBlank(message = "Goody name is required")
    @Size(max = 150, message = "Goody name must be at most 150 characters")
    @Schema(description = "The goodies column heading, exactly as the import stored it",
            example = "T-Shirt")
    private String goodieName;

    @NotNull(message = "Item is required")
    @Schema(description = "The inventory item this goody is handed out from", example = "12")
    private Long itemId;

    @Schema(description = "The location it is handed out from, which is where a handover deducts "
            + "stock; may be left for later while the event is still a draft, but the event cannot "
            + "be published without it", example = "4",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Long locationId;
}
