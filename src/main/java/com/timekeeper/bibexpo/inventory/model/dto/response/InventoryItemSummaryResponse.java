package com.timekeeper.bibexpo.inventory.model.dto.response;

import com.timekeeper.bibexpo.inventory.model.entity.InventoryItem;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "One entry of the item picker")
public class InventoryItemSummaryResponse {

    @Schema(description = "Item ID", example = "1")
    private Long id;

    @Schema(description = "Display name", example = "Race Tee")
    private String name;

    public static InventoryItemSummaryResponse fromEntity(InventoryItem item) {
        return new InventoryItemSummaryResponse(item.getId(), item.getName());
    }
}
