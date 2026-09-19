package com.timekeeper.bibexpo.event.model.entity;

/**
 * How a goody got onto an event's list, which decides what removing it has to undo.
 */
public enum GoodieSource {

    /** An imported file brought it, so participant records carry it under this name. */
    IMPORT,

    /** Added by hand after the fact; no participant record carries it. */
    MANUAL
}
