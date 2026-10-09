package com.timekeeper.bibexpo.inventory.exception;

public class StockCapacityExceededException extends RuntimeException {

    public static final String DEFAULT_MESSAGE =
            "You can keep at most 1,000,000 units of one variant at a location.";

    public StockCapacityExceededException() {
        super(DEFAULT_MESSAGE);
    }
}
