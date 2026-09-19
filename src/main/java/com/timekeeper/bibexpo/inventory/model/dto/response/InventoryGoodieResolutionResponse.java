package com.timekeeper.bibexpo.inventory.model.dto.response;

import com.timekeeper.bibexpo.inventory.model.enums.GoodieValueResolution;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "One goody of an event, every spelling its roster carries, and what each one "
        + "resolves to")
public class InventoryGoodieResolutionResponse {

    @Schema(description = "The goodies column heading, exactly as the import stored it",
            example = "T-Shirt")
    private String goodieName;

    @Schema(description = "The link that points this goody at an item; null when it has never been "
            + "linked", example = "3")
    private Long mappingId;

    @Schema(description = "The item this goody is handed out from; null when it has never been "
            + "linked", example = "12")
    private Long itemId;

    @Schema(description = "Name of that item", example = "Finisher T-Shirt")
    private String itemName;

    @Schema(description = "How many participants were promised this goody", example = "2827")
    private long participants;

    @Schema(description = "How many of them carry a spelling nothing recognises yet", example = "41")
    private long unresolvedParticipants;

    @Schema(description = "Every distinct spelling the roster carries for this goody, most "
            + "participants first")
    private List<Value> values;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "InventoryGoodieResolutionValue",
            description = "One spelling out of the roster, and what it resolves to")
    public static class Value {

        @Schema(description = "The cell value, exactly as imported", example = "M")
        private String value;

        @Schema(description = "How many participants carry it", example = "812")
        private long participants;

        @Schema(description = "How many of them have already been handed the goody", example = "300")
        private long handedOut;

        @Schema(description = "How it was read", example = "ALIAS")
        private GoodieValueResolution resolution;

        @Schema(description = "The variant it resolves to; null when it resolves to nothing",
                example = "42")
        private Long variantId;

        @Schema(description = "That variant's own values, for display", example = "38")
        private String variantLabel;
    }
}
