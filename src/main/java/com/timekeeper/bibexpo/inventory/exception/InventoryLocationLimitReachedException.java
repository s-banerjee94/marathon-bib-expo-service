package com.timekeeper.bibexpo.inventory.exception;

public class InventoryLocationLimitReachedException extends RuntimeException {

    public static final String DEFAULT_MESSAGE =
            "Your organization has reached the maximum number of inventory locations.";

    public InventoryLocationLimitReachedException() {
        super(DEFAULT_MESSAGE);
    }
}
