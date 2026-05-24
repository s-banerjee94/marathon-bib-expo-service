package com.timekeeper.bibexpo.exception;

public class GoodiesItemNotFoundException extends RuntimeException {

    public static final String DEFAULT_MESSAGE = "The goodies item you requested does not exist.";

    public GoodiesItemNotFoundException() {
        super(DEFAULT_MESSAGE);
    }

    public GoodiesItemNotFoundException(String message) {
        super(message);
    }
}
