package com.timekeeper.bibexpo.inventory.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "One item's stock: what it holds in total, and the rows that add up to it")
public class StockItemResponse {

    @Schema(description = "Item ID", example = "22")
    private Long itemId;

    @Schema(description = "Item name", example = "T-Shirt")
    private String itemName;

    @Schema(description = "Total on hand across the rows below, so a filtered list totals what it shows",
            example = "2400")
    private Integer onHand;

    @Schema(description = "How many locations hold this item", example = "1")
    private Integer locationCount;

    @Schema(description = "The item's own low-stock threshold, or null if none is set", example = "100")
    private Integer lowStockThreshold;

    @Schema(description = "Whether the total has fallen to the threshold. An item with no threshold set is low only when it holds nothing.",
            example = "false")
    private boolean low;

    @Schema(description = "The most recent change among the rows below, or null while the item holds no stock")
    private Instant updatedAt;

    @Schema(description = "The variant-and-location rows behind the total, empty while the item holds no stock")
    private List<StockRowResponse> rows;
}
