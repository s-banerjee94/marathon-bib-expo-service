package com.timekeeper.bibexpo.inventory.model.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
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

    @Schema(description = "Owning organization, null for a platform default", example = "1")
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

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "When this attribute was added; absent on a platform default read through an organization")
    private Instant createdAt;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Who added this attribute; absent on a platform default read through an organization", example = "organizer1")
    private String createdBy;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "When this attribute was last changed; absent on a platform default read through an organization")
    private Instant updatedAt;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Who last changed this attribute; absent on a platform default read through an organization", example = "organizer1")
    private String updatedBy;

    public static InventoryAttributeResponse fromEntity(InventoryAttribute attribute,
                                                                    List<InventoryAttributeOption> values) {
        return fromEntity(attribute, values, true);
    }

    // A platform default's audit trail is the platform administrator's, not the organization's, so
    // an organization reading the shared attributes is shown them, and their choices, without it.
    public static InventoryAttributeResponse fromEntity(InventoryAttribute attribute,
                                                                    List<InventoryAttributeOption> values,
                                                                    boolean withAudit) {
        InventoryAttributeResponseBuilder response = InventoryAttributeResponse.builder()
                .id(attribute.getId())
                .organizationId(attribute.getOrganizationId())
                .name(attribute.getName())
                .type(attribute.getType())
                .variantAttribute(attribute.isVariantAttribute())
                .required(attribute.isRequired())
                .values(values.stream()
                        .map(value -> InventoryAttributeOptionResponse.fromEntity(value, withAudit))
                        .toList());
        if (withAudit) {
            response.createdAt(attribute.getCreatedAt())
                    .createdBy(attribute.getCreatedBy())
                    .updatedAt(attribute.getUpdatedAt())
                    .updatedBy(attribute.getLastModifiedBy());
        }
        return response.build();
    }
}
