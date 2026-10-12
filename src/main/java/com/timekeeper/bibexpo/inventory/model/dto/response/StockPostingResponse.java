package com.timekeeper.bibexpo.inventory.model.dto.response;

import com.timekeeper.bibexpo.inventory.model.entity.InventoryPosting;
import com.timekeeper.bibexpo.inventory.model.enums.MovementType;
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
@Schema(description = "A posting and every balance it changed, so a grid can update without reloading")
public class StockPostingResponse {

    @Schema(description = "Posting ID", example = "57")
    private Long id;

    @Schema(description = "What the posting did; REVERSAL for an undo", example = "RECEIPT")
    private MovementType type;

    @Schema(description = "What caused this posting", example = "PO-114")
    private String reference;

    @Schema(description = "Free-text note")
    private String note;

    @Schema(description = "How many lines the posting held", example = "2")
    private Integer lineCount;

    @Schema(description = "When it was posted")
    private Instant createdAt;

    @Schema(description = "Who posted it", example = "organizer1")
    private String createdBy;

    @Schema(description = "For an undo, the posting it undid", example = "56")
    private Long reversesPostingId;

    @Schema(description = "The undo of this posting, or null while it stands", example = "58")
    private Long reversedByPostingId;

    @Schema(description = "The balance now at every variant and location the posting changed")
    private List<StockBalanceResponse> balances;

    public static StockPostingResponse fromEntity(InventoryPosting posting, List<StockBalanceResponse> balances) {
        return StockPostingResponse.builder()
                .id(posting.getId())
                .type(posting.getType())
                .reference(posting.getReference())
                .note(posting.getNote())
                .lineCount(posting.getLineCount())
                .createdAt(posting.getCreatedAt())
                .createdBy(posting.getCreatedBy())
                .reversesPostingId(posting.getReversesPostingId())
                .reversedByPostingId(posting.getReversedByPostingId())
                .balances(balances)
                .build();
    }
}
