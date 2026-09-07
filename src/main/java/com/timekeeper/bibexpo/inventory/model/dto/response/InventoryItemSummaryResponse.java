package com.timekeeper.bibexpo.inventory.model.dto.response;

import com.timekeeper.bibexpo.inventory.model.entity.InventoryItem;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "One row of the item list — the item itself, without its attributes or variants")
public class InventoryItemSummaryResponse {

    @Schema(description = "Item ID", example = "1")
    private Long id;

    @Schema(description = "Owning organization", example = "1")
    private Long organizationId;

    @Schema(description = "Display name", example = "Race Tee")
    private String name;

    @Schema(description = "Term ID for this item's category", example = "1")
    private Long categoryId;

    @Schema(description = "Term ID for this item's unit", example = "1")
    private Long unitId;

    @Schema(description = "Balance at or below which the item is considered low on stock", example = "50")
    private Integer lowStockThreshold;

    @Schema(description = "A note left for whoever works with this item next, or null if none was written",
            example = "Reserved for the sponsor lounge — do not hand out at the counter.")
    private String note;

    @Schema(description = "How many variants this item has", example = "3")
    private Integer variantCount;

    @Schema(description = "When this item was added")
    private Instant createdAt;

    @Schema(description = "Who added this item", example = "organizer1")
    private String createdBy;

    @Schema(description = "When this item was last changed")
    private Instant updatedAt;

    @Schema(description = "Who last changed this item", example = "organizer1")
    private String updatedBy;

    public static InventoryItemSummaryResponse fromEntity(InventoryItem item) {
        return InventoryItemSummaryResponse.builder()
                .id(item.getId())
                .organizationId(item.getOrganizationId())
                .name(item.getName())
                .categoryId(item.getCategoryId())
                .unitId(item.getUnitId())
                .lowStockThreshold(item.getLowStockThreshold())
                .note(item.getNote())
                .variantCount(item.getVariantCount())
                .createdAt(item.getCreatedAt())
                .createdBy(item.getCreatedBy())
                .updatedAt(item.getUpdatedAt())
                .updatedBy(item.getLastModifiedBy())
                .build();
    }
}
