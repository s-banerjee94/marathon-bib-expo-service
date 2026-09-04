package com.timekeeper.bibexpo.inventory.model.dto.response;

import com.timekeeper.bibexpo.inventory.model.entity.InventoryItem;
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
@Schema(description = "A thing the organization stocks, with its variants")
public class InventoryItemResponse {

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

    @Schema(description = "Values for this item's non-variant-defining attributes, applied once to the whole item")
    private List<ItemAttributeValueResponse> attributes;

    @Schema(description = "This item's variants")
    private List<InventoryVariantResponse> variants;

    @Schema(description = "When this item was added")
    private Instant createdAt;

    @Schema(description = "When this item was last changed")
    private Instant updatedAt;

    public static InventoryItemResponse fromEntity(InventoryItem item, List<InventoryVariantResponse> variants,
                                                     List<ItemAttributeValueResponse> attributes) {
        return InventoryItemResponse.builder()
                .id(item.getId())
                .organizationId(item.getOrganizationId())
                .name(item.getName())
                .categoryId(item.getCategoryId())
                .unitId(item.getUnitId())
                .lowStockThreshold(item.getLowStockThreshold())
                .attributes(attributes)
                .variants(variants)
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }
}
