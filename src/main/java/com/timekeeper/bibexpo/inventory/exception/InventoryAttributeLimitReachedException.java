package com.timekeeper.bibexpo.inventory.exception;

public class InventoryAttributeLimitReachedException extends RuntimeException {

    public static final String DEFAULT_MESSAGE =
            "Your organization has reached the maximum number of inventory attributes.";

    public InventoryAttributeLimitReachedException() {
        super(DEFAULT_MESSAGE);
    }
}
