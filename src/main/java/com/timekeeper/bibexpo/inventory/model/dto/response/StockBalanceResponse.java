package com.timekeeper.bibexpo.inventory.model.dto.response;

import com.timekeeper.bibexpo.inventory.model.entity.InventoryStock;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Stock balance for one variant at one location")
public class StockBalanceResponse {

    @Schema(description = "Variant ID", example = "1")
    private Long variantId;

    @Schema(description = "Location ID", example = "1")
    private Long locationId;

    @Schema(description = "Quantity on hand", example = "1300")
    private Integer onHand;

    public static StockBalanceResponse fromEntity(InventoryStock stock) {
        return new StockBalanceResponse(stock.getVariantId(), stock.getLocationId(), stock.getOnHand());
    }
}
