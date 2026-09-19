package com.timekeeper.bibexpo.event.exception;

public class EventDeletionNotAllowedException extends RuntimeException {
    public EventDeletionNotAllowedException(String message) {
        super(message);
    }
}
