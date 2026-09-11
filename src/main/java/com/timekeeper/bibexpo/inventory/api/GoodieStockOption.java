package com.timekeeper.bibexpo.inventory.api;

import java.util.List;

/**
 * A goody linked to an inventory item, with every variant it can be handed over as.
 *
 * @param goodieName the goody as its link names it
 * @param itemName   the inventory item it comes out of
 * @param variants   the item's variants in the order they were created; exactly one for an item
 *                   that varies by nothing
 */
public record GoodieStockOption(String goodieName, String itemName, List<Variant> variants) {

    /**
     * @param variantId the variant
     * @param label     how a person reads it; empty for an item that varies by nothing
     */
    public record Variant(Long variantId, String label) {
    }
}
