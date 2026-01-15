package com.timekeeper.bibexpo.exception;

public class GoodiesAlreadyDistributedException extends RuntimeException {

    public GoodiesAlreadyDistributedException(String itemName, String bibNumber) {
        super(String.format("Goodies item '%s' has already been distributed for bib number '%s'", itemName, bibNumber));
    }

    public GoodiesAlreadyDistributedException(String message) {
        super(message);
    }
}
