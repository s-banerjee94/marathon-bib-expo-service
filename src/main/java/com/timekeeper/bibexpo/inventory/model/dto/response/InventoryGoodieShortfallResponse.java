package com.timekeeper.bibexpo.inventory.model.dto.response;

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
@Schema(description = "One goody of an event, what its roster asks for variant by variant, and "
        + "what is on the shelf it is handed out from")
public class InventoryGoodieShortfallResponse {

    @Schema(description = "The goodies column heading, exactly as the import stored it",
            example = "T-Shirt")
    private String goodieName;

    @Schema(description = "The link that points this goody at an item; null when it has never been "
            + "linked", example = "3")
    private Long mappingId;

    @Schema(description = "The item this goody is handed out from; null when it has never been "
            + "linked", example = "12")
    private Long itemId;

    @Schema(description = "Name of that item", example = "T-Shirt")
    private String itemName;

    @Schema(description = "The location it is handed out from; null while nobody has chosen one, "
            + "in which case nothing is counted as on hand", example = "4")
    private Long locationId;

    @Schema(description = "Name of that location", example = "JBG Expo Venue")
    private String locationName;

    @Schema(description = "How many participants were promised this goody, whether or not their "
            + "spelling is understood yet", example = "2827")
    private long participants;

    @Schema(description = "How many of them have already been handed it, and so are no longer needed",
            example = "1000")
    private long handedOut;

    @Schema(description = "How many of those still to be handed it carry a spelling nothing "
            + "recognises, so they are behind none of the rows below and are not counted as needed",
            example = "41")
    private long unresolvedParticipants;

    @Schema(description = "Total still to hand out across every variant below", example = "1786")
    private long needed;

    @Schema(description = "Total on hand at the issuing location", example = "2000")
    private long onHand;

    @Schema(description = "How many are missing from that location; zero when it is covered",
            example = "786")
    private long shortfall;

    @Schema(description = "How many sit at the organization's other locations, which is what a "
            + "shortfall can be covered from without ordering more", example = "520")
    private long elsewhere;

    @Schema(description = "One row per variant the roster asks for, worst shortfall first")
    private List<Variant> variants;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "InventoryGoodieShortfallVariant",
            description = "One variant of the item, and how the demand for it stands")
    public static class Variant {

        @Schema(description = "The variant owed", example = "26")
        private Long variantId;

        @Schema(description = "Its own values, for display; null when the item varies by nothing",
                example = "M")
        private String variantLabel;

        @Schema(description = "How many participants are owed this variant and not yet handed it, "
                + "across every spelling that resolves to it", example = "1900")
        private long needed;

        @Schema(description = "How many are on hand at the issuing location", example = "1750")
        private long onHand;

        @Schema(description = "How many are missing from that location; zero when it is covered",
                example = "150")
        private long shortfall;

        @Schema(description = "How many sit at the organization's other locations", example = "300")
        private long elsewhere;
    }
}
