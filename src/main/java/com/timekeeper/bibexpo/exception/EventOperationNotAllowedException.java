package com.timekeeper.bibexpo.exception;

import org.springframework.http.HttpStatus;

public class EventOperationNotAllowedException extends ApiException {
    public EventOperationNotAllowedException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, message);
    }
}
