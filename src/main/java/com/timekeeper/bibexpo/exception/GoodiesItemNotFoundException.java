package com.timekeeper.bibexpo.exception;

public class GoodiesItemNotFoundException extends RuntimeException {

    public GoodiesItemNotFoundException(String itemName, String bibNumber) {
        super(String.format("Goodies item '%s' not found for bib number '%s'", itemName, bibNumber));
    }

    public GoodiesItemNotFoundException(String message) {
        super(message);
    }
}
