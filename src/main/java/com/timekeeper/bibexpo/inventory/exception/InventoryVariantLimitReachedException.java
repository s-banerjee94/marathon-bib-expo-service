package com.timekeeper.bibexpo.inventory.exception;

public class InventoryVariantLimitReachedException extends RuntimeException {

    public InventoryVariantLimitReachedException(String message) {
        super(message);
    }
}
