package com.timekeeper.bibexpo.inventory.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "One resolved attribute value carried by an item or a variant")
public class ItemAttributeValueResponse {

    @Schema(description = "Attribute ID", example = "1")
    private Long attributeId;

    @Schema(description = "Attribute name", example = "Size")
    private String attributeName;

    @Schema(description = "Chosen value ID, for a SELECT attribute", example = "1")
    private Long optionId;

    @Schema(description = "Display value — the SELECT choice's text, or the raw value", example = "L")
    private String value;
}
