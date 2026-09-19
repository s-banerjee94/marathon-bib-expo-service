package com.timekeeper.bibexpo.inventory.model.enums;

/**
 * Why a movement was posted — the machine-readable cause, separate from the free-text note.
 */
public enum MovementReason {
    OPENING_BALANCE,
    TRANSFER,
    DAMAGED,
    LOST,
    CORRECTION
}
