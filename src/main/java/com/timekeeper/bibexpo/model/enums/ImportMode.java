package com.timekeeper.bibexpo.model.enums;

/**
 * Distinguishes the two ways the CSV batch pipeline can run.
 *
 * <p>{@code IMPORT} is a full load: existing participants are wiped before the file is loaded,
 * and it is only permitted while the event is in draft. {@code ADD_ON} appends walk-ins without
 * wiping and is permitted while the event is draft or published.
 */
public enum ImportMode {
    IMPORT,
    ADD_ON
}
