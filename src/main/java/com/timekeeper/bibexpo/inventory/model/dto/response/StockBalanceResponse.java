package com.timekeeper.bibexpo.inventory.model.dto.response;

import com.timekeeper.bibexpo.inventory.model.entity.InventoryStock;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Stock balance for one variant at one location")
public class StockBalanceResponse {

    @Schema(description = "Stock row ID", example = "1")
    private Long id;

    @Schema(description = "Variant ID", example = "1")
    private Long variantId;

    @Schema(description = "Location ID", example = "1")
    private Long locationId;

    @Schema(description = "Quantity on hand", example = "1300")
    private Integer onHand;

    @Schema(description = "Quantity reserved", example = "0")
    private Integer reserved;

    @Schema(description = "Weighted average unit cost, when known", example = "150.00")
    private BigDecimal avgUnitCost;

    @Schema(description = "When this variant first held stock at this location")
    private Instant createdAt;

    @Schema(description = "Who first brought stock of this variant to this location", example = "organizer1")
    private String createdBy;

    @Schema(description = "When this balance last changed")
    private Instant updatedAt;

    @Schema(description = "Who last changed this balance", example = "organizer1")
    private String updatedBy;

    public static StockBalanceResponse fromEntity(InventoryStock stock) {
        return StockBalanceResponse.builder()
                .id(stock.getId())
                .variantId(stock.getVariantId())
                .locationId(stock.getLocationId())
                .onHand(stock.getOnHand())
                .reserved(stock.getReserved())
                .avgUnitCost(stock.getAvgUnitCost())
                .createdAt(stock.getCreatedAt())
                .createdBy(stock.getCreatedBy())
                .updatedAt(stock.getUpdatedAt())
                .updatedBy(stock.getLastModifiedBy())
                .build();
    }
}
