package com.timekeeper.bibexpo.inventory.model.dto.response;

import com.timekeeper.bibexpo.inventory.model.entity.InventoryAttribute;
import com.timekeeper.bibexpo.inventory.model.entity.InventoryAttributeOption;
import com.timekeeper.bibexpo.inventory.model.enums.AttributeType;
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
@Schema(description = "A reusable, named dimension an item can vary by, or simply carry")
public class InventoryAttributeResponse {

    @Schema(description = "Attribute ID", example = "1")
    private Long id;

    @Schema(description = "Owning organization", example = "1")
    private Long organizationId;

    @Schema(description = "Display name", example = "Size")
    private String name;

    @Schema(description = "The kind of value this attribute holds", example = "SELECT")
    private AttributeType type;

    @Schema(description = "True if choosing a value splits stock into a separate variant", example = "true")
    private boolean variantAttribute;

    @Schema(description = "Whether an item using this attribute must supply a value", example = "true")
    private boolean required;

    @Schema(description = "Allowed choices, populated for SELECT attributes")
    private List<InventoryAttributeOptionResponse> values;

    @Schema(description = "When this attribute was added")
    private Instant createdAt;

    @Schema(description = "Who added this attribute", example = "organizer1")
    private String createdBy;

    @Schema(description = "When this attribute was last changed")
    private Instant updatedAt;

    @Schema(description = "Who last changed this attribute", example = "organizer1")
    private String updatedBy;

    public static InventoryAttributeResponse fromEntity(InventoryAttribute attribute,
                                                        List<InventoryAttributeOption> values) {
        return InventoryAttributeResponse.builder()
                .id(attribute.getId())
                .organizationId(attribute.getOrganizationId())
                .name(attribute.getName())
                .type(attribute.getType())
                .variantAttribute(attribute.isVariantAttribute())
                .required(attribute.isRequired())
                .values(values.stream()
                        .map(InventoryAttributeOptionResponse::fromEntity)
                        .toList())
                .createdAt(attribute.getCreatedAt())
                .createdBy(attribute.getCreatedBy())
                .updatedAt(attribute.getUpdatedAt())
                .updatedBy(attribute.getLastModifiedBy())
                .build();
    }
}
