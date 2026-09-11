package com.timekeeper.bibexpo.inventory.api;

/**
 * One unit of a goody leaving a location's shelf at the counter: which variant, and from where.
 *
 * @param variantId    the variant handed over
 * @param variantLabel how a person reads that variant, such as {@code L} or {@code 750 ml}; empty for
 *                     an item that varies by nothing
 * @param locationId   the location the goody's link hands it out from
 */
public record GoodieIssue(Long variantId, String variantLabel, Long locationId) {
}
