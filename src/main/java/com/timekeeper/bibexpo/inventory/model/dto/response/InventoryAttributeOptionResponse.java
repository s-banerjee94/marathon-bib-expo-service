package com.timekeeper.bibexpo.inventory.model.dto.response;

import com.timekeeper.bibexpo.inventory.model.entity.InventoryAttributeOption;
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
@Schema(description = "One allowed choice for a SELECT attribute")
public class InventoryAttributeOptionResponse {

    @Schema(description = "Value ID", example = "1")
    private Long id;

    @Schema(description = "Owning attribute", example = "1")
    private Long attributeId;

    @Schema(description = "The choice", example = "500ml")
    private String value;

    @Schema(description = "When this value was added")
    private Instant createdAt;

    @Schema(description = "Who added this value", example = "organizer1")
    private String createdBy;

    @Schema(description = "When this value was last changed")
    private Instant updatedAt;

    @Schema(description = "Who last changed this value", example = "organizer1")
    private String updatedBy;

    public static InventoryAttributeOptionResponse fromEntity(InventoryAttributeOption value) {
        return InventoryAttributeOptionResponse.builder()
                .id(value.getId())
                .attributeId(value.getAttributeId())
                .value(value.getValue())
                .createdAt(value.getCreatedAt())
                .createdBy(value.getCreatedBy())
                .updatedAt(value.getUpdatedAt())
                .updatedBy(value.getLastModifiedBy())
                .build();
    }
}
