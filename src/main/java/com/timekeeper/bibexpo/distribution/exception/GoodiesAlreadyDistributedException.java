package com.timekeeper.bibexpo.distribution.exception;

public class GoodiesAlreadyDistributedException extends RuntimeException {

    public GoodiesAlreadyDistributedException(String itemName) {
        super("The goodies item '" + itemName + "' has already been given out for this bib.");
    }
}
