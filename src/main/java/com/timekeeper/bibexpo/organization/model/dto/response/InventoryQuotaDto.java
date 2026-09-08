package com.timekeeper.bibexpo.organization.model.dto.response;

import com.timekeeper.bibexpo.organization.model.entity.OrganizationLimit;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Inventory caps. The first two are counted for the whole organization and "
        + "carry their usage; the rest are ceilings on one item or one attribute and report a null usage.")
public class InventoryQuotaDto {

    @Schema(description = "The organization's own vocabulary entries — categories, location types and units. "
            + "Platform defaults are shared and not counted.")
    private QuotaDto terms;

    @Schema(description = "Places the organization may keep stock in")
    private QuotaDto locations;

    @Schema(description = "Choices allowed on one of the organization's own attributes")
    private QuotaDto optionsPerAttribute;

    @Schema(description = "Attributes one item may split its stock by")
    private QuotaDto variantAttributesPerItem;

    @Schema(description = "Variant rows one item may hold")
    private QuotaDto variantsPerItem;

    public static InventoryQuotaDto fromEntity(OrganizationLimit limit) {
        return InventoryQuotaDto.builder()
                .terms(QuotaDto.of(limit.getMaxInventoryTerms(), limit.getUsedInventoryTerms()))
                .locations(QuotaDto.of(limit.getMaxInventoryLocations(), limit.getUsedInventoryLocations()))
                .optionsPerAttribute(QuotaDto.capOnly(limit.getMaxInventoryAttributeOptions()))
                .variantAttributesPerItem(QuotaDto.capOnly(limit.getMaxVariantAttributesPerItem()))
                .variantsPerItem(QuotaDto.capOnly(limit.getMaxItemVariants()))
                .build();
    }
}
