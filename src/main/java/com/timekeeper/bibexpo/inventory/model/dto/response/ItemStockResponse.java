package com.timekeeper.bibexpo.inventory.model.dto.response;

import com.timekeeper.bibexpo.inventory.model.enums.StockAction;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "One item's stock as a grid: every variant down, every location across, and the balances in between")
public class ItemStockResponse {

    @Schema(description = "Item ID", example = "1")
    private Long itemId;

    @Schema(description = "Item name", example = "T-Shirt")
    private String itemName;

    @Schema(description = "The item's photo, else the first variant photo, else null")
    private String imageUrl;

    @Schema(description = "Name of the item's unit", example = "pcs")
    private String unitName;

    @Schema(description = "The item's own low-stock threshold, or null if none is set", example = "20")
    private Integer lowStockThreshold;

    @Schema(description = "Total on hand across every balance", example = "-5")
    private Integer onHand;

    @Schema(description = "Every variant of the item, in the order its attribute values were defined")
    private List<Variant> variants;

    @Schema(description = "Every location of the organization, by name")
    private List<Location> locations;

    @Schema(description = "Only the cells that hold a balance; a missing cell holds 0")
    private List<StockBalanceResponse> balances;

    @Schema(description = "What the caller may do here. COUNT is a recount; ADJUSTMENT also writes off damaged and lost stock.")
    private Set<StockAction> allowedActions;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Variant {

        @Schema(description = "Variant ID", example = "11")
        private Long id;

        @Schema(description = "The variant's values joined for display, or null when the item varies by nothing", example = "S")
        private String label;

        @Schema(description = "The variant's own photo, or null")
        private String imageUrl;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Location {

        @Schema(description = "Location ID", example = "2")
        private Long id;

        @Schema(description = "Location name", example = "Expo Venue")
        private String name;
    }
}
