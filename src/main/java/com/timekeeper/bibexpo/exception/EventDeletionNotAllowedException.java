package com.timekeeper.bibexpo.exception;

public class EventDeletionNotAllowedException extends RuntimeException {
    public EventDeletionNotAllowedException(String message) {
        super(message);
    }
}
