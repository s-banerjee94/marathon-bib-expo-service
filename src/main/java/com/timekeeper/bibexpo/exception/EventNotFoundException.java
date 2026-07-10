package com.timekeeper.bibexpo.exception;

import org.springframework.http.HttpStatus;

public class EventNotFoundException extends ApiException {

    public static final String DEFAULT_MESSAGE = "The event you requested does not exist.";

    public EventNotFoundException() {
        super(HttpStatus.NOT_FOUND, DEFAULT_MESSAGE);
    }

    public EventNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }
}
