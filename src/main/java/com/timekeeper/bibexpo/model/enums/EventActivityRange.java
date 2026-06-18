package com.timekeeper.bibexpo.model.enums;

/**
 * Time window for the event dashboard's range-scoped activity block.
 * A bib expo runs 1–3 days, so only two windows are meaningful.
 */
public enum EventActivityRange {

    /** Start of the current event-local day up to now, with a same-day comparison against the prior day. */
    TODAY,

    /** The whole expo span (event start through now), continuous across every expo day. */
    FULL_EXPO
}
