package com.timekeeper.bibexpo.inventory.model.dto.response;

import com.timekeeper.bibexpo.inventory.model.enums.MovementReason;
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
@Schema(description = "One stock change to an item as the person made it: a posting, or a single counter hand-out")
public class ItemActivityResponse {

    @Schema(description = "The posting, or null for a counter hand-out or a line from before postings existed", example = "57")
    private Long postingId;

    @Schema(description = "What the change did; ISSUE is a counter hand-out, REVERSAL an undo", example = "TRANSFER")
    private MovementType type;

    @Schema(description = "The reason every line shares, or null when they differ or carry none", example = "DAMAGED")
    private MovementReason reason;

    @Schema(description = "What caused the change; the bib number for a hand-out", example = "PO-114")
    private String reference;

    @Schema(description = "Username of whoever made the change", example = "organizer1")
    private String performedBy;

    @Schema(description = "When the stock moved")
    private Instant occurredAt;

    @Schema(description = "For an undo, the posting it undid", example = "56")
    private Long reversesPostingId;

    @Schema(description = "The undo of this posting, or null while it stands", example = "58")
    private Long reversedByPostingId;

    @Schema(description = "Whether the caller could undo this now: their role allows it, it is not undone or an undo, "
            + "and every shelf it touched can take the undo", example = "true")
    private boolean canReverse;

    @Schema(description = "This item's lines of the change; a posting may also have moved other items")
    private List<Line> lines;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "ItemActivityLine", description = "One ledger line of the change")
    public static class Line {

        @Schema(description = "Variant ID", example = "11")
        private Long variantId;

        @Schema(description = "The variant's values joined for display, or null when the item varies by nothing", example = "L")
        private String variantLabel;

        @Schema(description = "Why this line moved, when recorded", example = "DAMAGED")
        private MovementReason reason;

        @Schema(description = "Location the stock left, or null when it came from outside", example = "1")
        private Long fromLocationId;

        @Schema(description = "Name of the location the stock left, or null", example = "Warehouse")
        private String fromLocationName;

        @Schema(description = "Location the stock arrived at, or null when it went out", example = "2")
        private Long toLocationId;

        @Schema(description = "Name of the location the stock arrived at, or null", example = "Expo Venue")
        private String toLocationName;

        @Schema(description = "Units moved", example = "40")
        private Integer quantity;

        @Schema(description = "What the shelf this line touched held right after it, or null on lines from before it was kept",
                example = "35")
        private Integer balanceAfter;
    }
}
