package com.timekeeper.bibexpo.inventory.model.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
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

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "When this term was added; absent on a platform default read through an organization")
    private Instant createdAt;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Who added this term; absent on a platform default read through an organization", example = "organizer1")
    private String createdBy;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "When this term was last renamed; absent on a platform default read through an organization")
    private Instant updatedAt;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Who last renamed this term; absent on a platform default read through an organization", example = "organizer1")
    private String updatedBy;

    public static InventoryTermResponse fromEntity(InventoryTerm term) {
        return fromEntity(term, true);
    }

    // A platform default's audit trail is the platform administrator's, not the organization's, so
    // an organization reading the shared vocabulary is shown the term without it.
    public static InventoryTermResponse fromEntity(InventoryTerm term, boolean withAudit) {
        InventoryTermResponseBuilder response = InventoryTermResponse.builder()
                .id(term.getId())
                .kind(term.getKind())
                .organizationId(term.getOrganizationId())
                .name(term.getName());
        if (withAudit) {
            response.createdAt(term.getCreatedAt())
                    .createdBy(term.getCreatedBy())
                    .updatedAt(term.getUpdatedAt())
                    .updatedBy(term.getLastModifiedBy());
        }
        return response.build();
    }
}
