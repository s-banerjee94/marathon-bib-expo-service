package com.timekeeper.bibexpo.inventory.model.enums;

/**
 * How one imported goodies value was read against the item behind that goody &mdash; the answer the
 * check screen shows for every distinct spelling a roster contains.
 */
public enum GoodieValueResolution {

    /** The item's own variants already read this way, give or take case and surrounding spaces. */
    VARIANT,

    /** The item's own values do not read this way, but one of its spellings says what it means. */
    ALIAS,

    /** A spelling of the item records that this one means the participant is owed nothing. */
    NOTHING_OWED,

    /** The item varies by nothing, so it is handed over as it is and the value is not read. */
    SINGLE_VARIANT,

    /** The goody has not been pointed at an item yet, so nothing can be read from it. */
    NOT_LINKED,

    /** Nothing recognises this spelling. It is never guessed &mdash; the item has to be taught it. */
    UNRESOLVED
}
