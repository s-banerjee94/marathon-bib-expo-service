package com.timekeeper.bibexpo.exception;

public class EventNotFoundException extends RuntimeException {

    public static final String DEFAULT_MESSAGE = "The event you requested does not exist.";

    public EventNotFoundException() {
        super(DEFAULT_MESSAGE);
    }

    public EventNotFoundException(String message) {
        super(message);
    }
}
