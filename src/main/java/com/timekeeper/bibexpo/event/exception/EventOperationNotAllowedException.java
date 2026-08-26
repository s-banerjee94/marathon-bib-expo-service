package com.timekeeper.bibexpo.event.exception;

import com.timekeeper.bibexpo.shared.error.ApiException;
import org.springframework.http.HttpStatus;

public class EventOperationNotAllowedException extends ApiException {
    public EventOperationNotAllowedException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, message);
    }
}
