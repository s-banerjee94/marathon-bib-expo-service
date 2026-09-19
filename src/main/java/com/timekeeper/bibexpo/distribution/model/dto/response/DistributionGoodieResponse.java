package com.timekeeper.bibexpo.distribution.model.dto.response;

import com.timekeeper.bibexpo.event.model.entity.GoodieSource;
import com.timekeeper.bibexpo.inventory.model.enums.GoodieValueResolution;
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

    @Schema(description = "Every value participants carry for this goody on their own record, and what a hand-over "
            + "without a chosen variant does with it. Match a participant's own value against value exactly. "
            + "Filled for a goody linked to inventory that the participant list brought; empty otherwise.")
    private List<Value> values;

    @Data
    @Builder
    @Schema(name = "DistributionGoodieVariant", description = "One variant a goody can be handed over as")
    public static class Variant {

        @Schema(description = "Variant ID, sent back as the goody's entry in variantIds", example = "12")
        private Long variantId;

        @Schema(description = "How a person reads the variant", example = "750 ml")
        private String label;
    }

    @Data
    @Builder
    @Schema(name = "DistributionGoodieValue",
            description = "One value participants carry for a goody, and the variant it is handed over as")
    public static class Value {

        @Schema(description = "The value exactly as it stands on the participant's record", example = "Medium38")
        private String value;

        @Schema(description = "The variant a hand-over takes for it when none is chosen; null when it names none",
                example = "26")
        private Long variantId;

        @Schema(description = "How it was read. VARIANT, ALIAS and SINGLE_VARIANT go out as variantId. "
                + "NOTHING_OWED is handed over without taking anything off the shelf. UNRESOLVED matches no variant, "
                + "so the hand-over is refused until a variant is chosen.", example = "UNRESOLVED")
        private GoodieValueResolution resolution;
    }
}
