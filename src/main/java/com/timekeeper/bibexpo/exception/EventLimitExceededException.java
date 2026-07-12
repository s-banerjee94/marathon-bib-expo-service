package com.timekeeper.bibexpo.exception;

import org.springframework.http.HttpStatus;

public class EventLimitExceededException extends ApiException {
    public EventLimitExceededException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
