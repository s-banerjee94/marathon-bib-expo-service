package com.timekeeper.bibexpo.inventory.api;

/**
 * One goody the counter is about to hand over, as {@link GoodieStockQuery#planIssues} reads it.
 *
 * @param goodieName the goody, as the participant's record names it
 * @param value      the participant's own value for the goody, or null for one added to the event by hand
 * @param variantId  the variant the counter chose, or null when it chose none
 */
public record GoodieRequest(String goodieName, String value, Long variantId) {
}
