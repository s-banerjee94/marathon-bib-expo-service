package com.timekeeper.bibexpo.event.model.enums;

public enum EventOperation {
    RACE_WRITE,
    CATEGORY_WRITE,
    PARTICIPANT_WRITE,
    FULL_IMPORT,
    ADDON_IMPORT,
    DISTRIBUTION,
    TEMPLATE_WRITE,
    CAMPAIGN_WRITE,
    /** Add a goody to the event's list, or remove one no participant record carries. */
    GOODIE_WRITE,
    /** Remove an imported goody, which rewrites every participant record that carries it. */
    GOODIE_PURGE
}
