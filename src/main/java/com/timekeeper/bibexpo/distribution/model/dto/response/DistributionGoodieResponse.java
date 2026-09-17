package com.timekeeper.bibexpo.distribution.model.dto.response;

import com.timekeeper.bibexpo.event.model.entity.GoodieSource;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@Schema(description = "A goody on the event's list, as the distribution counter offers it")
public class DistributionGoodieResponse {

    @Schema(description = "Goody name, exactly as it is sent back when handing it over", example = "Sipper")
    private String name;

    @Schema(description = "IMPORT when the participant list brought it, MANUAL when someone added it by hand. "
            + "A participant is owed the IMPORT goodies on their own record; a MANUAL goody can be handed to anyone.",
            example = "MANUAL")
    private GoodieSource source;

    @Schema(description = "The inventory item it comes out of, or null when it is not linked to inventory",
            example = "Sipper Bottle")
    private String itemName;

    @Schema(description = "Variants to choose between when handing it over, filled whenever its item comes in more "
            + "than one; send the chosen variantId with the hand-over. A MANUAL goody always needs one. For an IMPORT "
            + "goody it is optional: without one the participant's own value decides, and the hand-over is refused "
            + "only when that value was never taught to the item. Empty otherwise.")
    private List<Variant> variants;

    @Data
    @Builder
    @Schema(name = "DistributionGoodieVariant", description = "One variant a goody can be handed over as")
    public static class Variant {

        @Schema(description = "Variant ID, sent back as the goody's entry in variantIds", example = "12")
        private Long variantId;

        @Schema(description = "How a person reads the variant", example = "750 ml")
        private String label;
    }
}
