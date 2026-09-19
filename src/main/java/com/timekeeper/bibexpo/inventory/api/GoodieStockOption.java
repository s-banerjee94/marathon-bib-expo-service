package com.timekeeper.bibexpo.inventory.api;

import com.timekeeper.bibexpo.inventory.model.enums.GoodieValueResolution;

import java.util.List;

/**
 * A goody linked to an inventory item, with every variant it can be handed over as.
 *
 * @param goodieName the goody as its link names it
 * @param itemName   the inventory item it comes out of
 * @param variants   the item's variants in the order they were created; exactly one for an item
 *                   that varies by nothing
 * @param values     every value the event's roster carries for this goody, read the way a hand-over
 *                   reads it; empty for a goody no roster carries
 */
public record GoodieStockOption(String goodieName, String itemName, List<Variant> variants,
                                List<ValueReading> values) {

    /**
     * @param variantId the variant
     * @param label     how a person reads it; empty for an item that varies by nothing
     */
    public record Variant(Long variantId, String label) {
    }

    /**
     * @param value      the value exactly as the roster carries it
     * @param variantId  the variant a hand-over takes for it; null when it names none
     * @param resolution how it was read
     */
    public record ValueReading(String value, Long variantId, GoodieValueResolution resolution) {
    }
}
