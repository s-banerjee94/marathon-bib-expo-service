package com.timekeeper.bibexpo.inventory.model.enums;

/**
 * The kind of ledger line an inventory movement represents.
 */
public enum MovementType {
    RECEIPT,
    ISSUE,
    RETURN,
    TRANSFER,
    ADJUSTMENT,
    REVERSAL
}
