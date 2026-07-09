package com.timekeeper.bibexpo.exception;

public class EventOperationNotAllowedException extends RuntimeException {
    public EventOperationNotAllowedException(String message) {
        super(message);
    }
}
