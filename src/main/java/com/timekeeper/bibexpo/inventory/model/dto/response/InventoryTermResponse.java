package com.timekeeper.bibexpo.inventory.model.dto.response;

import com.timekeeper.bibexpo.inventory.model.entity.InventoryTerm;
import com.timekeeper.bibexpo.inventory.model.enums.TermKind;
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
@Schema(description = "One entry in an organization's, or the platform's, vocabulary for a category, location type or unit")
public class InventoryTermResponse {

    @Schema(description = "Term ID", example = "1")
    private Long id;

    @Schema(description = "Which dropdown this term fills", example = "ITEM_CATEGORY")
    private TermKind kind;

    @Schema(description = "Owning organization; null when this is a platform default editable only by the platform administrator", example = "1")
    private Long organizationId;

    @Schema(description = "Display name", example = "Apparel")
    private String name;

    @Schema(description = "When this term was added")
    private Instant createdAt;

    @Schema(description = "When this term was last renamed")
    private Instant updatedAt;

    public static InventoryTermResponse fromEntity(InventoryTerm term) {
        return InventoryTermResponse.builder()
                .id(term.getId())
                .kind(term.getKind())
                .organizationId(term.getOrganizationId())
                .name(term.getName())
                .createdAt(term.getCreatedAt())
                .updatedAt(term.getUpdatedAt())
                .build();
    }
}
