package com.timekeeper.bibexpo.exception;

public class EventLimitExceededException extends RuntimeException {
    public EventLimitExceededException(String message) {
        super(message);
    }
}
