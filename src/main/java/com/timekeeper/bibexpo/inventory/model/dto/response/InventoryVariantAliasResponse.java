package com.timekeeper.bibexpo.inventory.model.dto.response;

import com.timekeeper.bibexpo.inventory.model.entity.InventoryVariantAlias;
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
@Schema(description = "One of an item's spellings: how a roster says it, and what it means")
public class InventoryVariantAliasResponse {

    @Schema(description = "Spelling ID", example = "1")
    private Long id;

    @Schema(description = "Owning organization", example = "1")
    private Long organizationId;

    @Schema(description = "The item this spelling belongs to", example = "12")
    private Long itemId;

    @Schema(description = "The spelling as it appears in a roster", example = "Medium")
    private String sourceValue;

    @Schema(description = "The variant that spelling means, or null when it is owed nothing",
            example = "42")
    private Long variantId;

    @Schema(description = "That variant's own values, for display; null when the spelling is owed "
            + "nothing", example = "38")
    private String variantLabel;

    @Schema(description = "When this line was added")
    private Instant createdAt;

    @Schema(description = "Who added it", example = "organizer1")
    private String createdBy;

    @Schema(description = "When it was last changed")
    private Instant updatedAt;

    @Schema(description = "Who last changed it", example = "organizer1")
    private String updatedBy;

    public static InventoryVariantAliasResponse of(InventoryVariantAlias alias, String variantLabel) {
        return InventoryVariantAliasResponse.builder()
                .id(alias.getId())
                .organizationId(alias.getOrganizationId())
                .itemId(alias.getItemId())
                .sourceValue(alias.getSourceValue())
                .variantId(alias.getVariantId())
                .variantLabel(variantLabel)
                .createdAt(alias.getCreatedAt())
                .createdBy(alias.getCreatedBy())
                .updatedAt(alias.getUpdatedAt())
                .updatedBy(alias.getLastModifiedBy())
                .build();
    }
}
