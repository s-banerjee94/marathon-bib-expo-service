package com.timekeeper.bibexpo.inventory.exception;

public class InventoryAttributeOptionLimitReachedException extends RuntimeException {

    public static final String DEFAULT_MESSAGE =
            "Your organization has reached the maximum number of values for this attribute.";

    public InventoryAttributeOptionLimitReachedException() {
        super(DEFAULT_MESSAGE);
    }
}
