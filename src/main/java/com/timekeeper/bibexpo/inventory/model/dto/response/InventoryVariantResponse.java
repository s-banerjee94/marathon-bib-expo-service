package com.timekeeper.bibexpo.inventory.model.dto.response;

import com.timekeeper.bibexpo.inventory.model.entity.InventoryVariant;
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
@Schema(description = "A size, colour or flavour of an item")
public class InventoryVariantResponse {

    @Schema(description = "Variant ID", example = "1")
    private Long id;

    @Schema(description = "Owning item", example = "1")
    private Long itemId;

    @Schema(description = "This variant's attribute values, for each attribute the item varies by")
    private List<ItemAttributeValueResponse> attributeValues;

    @Schema(description = "Short-lived download URL for this variant's image, or null if none is attached")
    private String imageUrl;

    @Schema(description = "When this variant was added")
    private Instant createdAt;

    @Schema(description = "Who added this variant", example = "organizer1")
    private String createdBy;

    @Schema(description = "When this variant was last changed")
    private Instant updatedAt;

    @Schema(description = "Who last changed this variant", example = "organizer1")
    private String updatedBy;

    public static InventoryVariantResponse fromEntity(InventoryVariant variant, List<ItemAttributeValueResponse> attributeValues,
                                                        String imageUrl) {
        return InventoryVariantResponse.builder()
                .id(variant.getId())
                .itemId(variant.getItemId())
                .attributeValues(attributeValues)
                .imageUrl(imageUrl)
                .createdAt(variant.getCreatedAt())
                .createdBy(variant.getCreatedBy())
                .updatedAt(variant.getUpdatedAt())
                .updatedBy(variant.getLastModifiedBy())
                .build();
    }
}
