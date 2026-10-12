package com.timekeeper.bibexpo.inventory.model.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
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
@Schema(description = "The standard error body plus the lines that could not be posted, so a screen can mark them")
public class StockPostingConflictResponse {

    private Instant timestamp;
    private int status;
    private String error;
    private String message;
    private String path;

    @Schema(description = "Every line that failed; nothing in the posting was written")
    private List<Line> lines;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(name = "StockPostingConflictLine",
            description = "One failed line: a count whose shelf changed carries expectedOnHand, a short shelf carries requested")
    public static class Line {

        @Schema(description = "Position of the line in the request, from 0; for an undo, the ledger line being undone",
                example = "1")
        private int index;

        @Schema(description = "Variant ID", example = "12")
        private Long variantId;

        @Schema(description = "Location ID", example = "2")
        private Long locationId;

        @Schema(description = "What the shelf holds now", example = "35")
        private Integer onHand;

        @Schema(description = "What the count expected the shelf to hold", example = "40")
        private Integer expectedOnHand;

        @Schema(description = "How many units the line needed", example = "10")
        private Integer requested;
    }
}
