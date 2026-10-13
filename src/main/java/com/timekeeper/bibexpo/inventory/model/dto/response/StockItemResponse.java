package com.timekeeper.bibexpo.inventory.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "One item's stock in total; the per-variant grid comes from the item's own stock endpoint")
public class StockItemResponse {

    @Schema(description = "Item ID", example = "22")
    private Long itemId;

    @Schema(description = "Item name", example = "T-Shirt")
    private String itemName;

    @Schema(description = "The item's photo, else the first variant photo, else null")
    private String imageUrl;

    @Schema(description = "Total on hand across every variant, at the filtered location when one is given",
            example = "2400")
    private Integer onHand;

    @Schema(description = "How many locations hold a balance row for this item, 1 when filtered to a location",
            example = "1")
    private Integer locationCount;

    @Schema(description = "Whether the total has fallen to the item's threshold. An item with no threshold set is low only when it holds nothing.",
            example = "false")
    private boolean low;
}
