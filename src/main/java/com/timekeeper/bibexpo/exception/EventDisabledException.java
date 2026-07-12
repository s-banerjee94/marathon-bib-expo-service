package com.timekeeper.bibexpo.exception;

import org.springframework.http.HttpStatus;

public class EventDisabledException extends ApiException {
    public EventDisabledException(String message) {
        super(HttpStatus.FORBIDDEN, message);
    }
}
