package com.timekeeper.bibexpo.inventory.exception;

public class InventoryTermLimitReachedException extends RuntimeException {

    public static final String DEFAULT_MESSAGE =
            "Your organization has reached the maximum number of inventory terms.";

    public InventoryTermLimitReachedException() {
        super(DEFAULT_MESSAGE);
    }
}
