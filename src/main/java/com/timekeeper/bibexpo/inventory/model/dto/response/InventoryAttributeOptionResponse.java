package com.timekeeper.bibexpo.inventory.model.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
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

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "When this value was added; absent on a platform default read through an organization")
    private Instant createdAt;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Who added this value; absent on a platform default read through an organization", example = "organizer1")
    private String createdBy;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "When this value was last changed; absent on a platform default read through an organization")
    private Instant updatedAt;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Who last changed this value; absent on a platform default read through an organization", example = "organizer1")
    private String updatedBy;

    public static InventoryAttributeOptionResponse fromEntity(InventoryAttributeOption value) {
        return fromEntity(value, true);
    }

    public static InventoryAttributeOptionResponse fromEntity(InventoryAttributeOption value, boolean withAudit) {
        InventoryAttributeOptionResponseBuilder response = InventoryAttributeOptionResponse.builder()
                .id(value.getId())
                .attributeId(value.getAttributeId())
                .value(value.getValue());
        if (withAudit) {
            response.createdAt(value.getCreatedAt())
                    .createdBy(value.getCreatedBy())
                    .updatedAt(value.getUpdatedAt())
                    .updatedBy(value.getLastModifiedBy());
        }
        return response.build();
    }
}
