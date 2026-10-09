package com.timekeeper.bibexpo.inventory.exception;

public class InventoryItemLimitReachedException extends RuntimeException {

    public static final String DEFAULT_MESSAGE =
            "Your organization has reached the maximum number of inventory items.";

    public InventoryItemLimitReachedException() {
        super(DEFAULT_MESSAGE);
    }
}
