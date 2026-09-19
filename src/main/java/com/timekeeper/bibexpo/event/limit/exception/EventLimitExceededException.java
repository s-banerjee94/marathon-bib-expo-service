package com.timekeeper.bibexpo.event.limit.exception;

import com.timekeeper.bibexpo.shared.error.ApiException;
import org.springframework.http.HttpStatus;

public class EventLimitExceededException extends ApiException {
    public EventLimitExceededException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
