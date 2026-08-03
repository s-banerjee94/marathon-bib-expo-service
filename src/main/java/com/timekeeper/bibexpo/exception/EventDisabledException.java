package com.timekeeper.bibexpo.exception;

import com.timekeeper.bibexpo.shared.error.ApiException;
import org.springframework.http.HttpStatus;

public class EventDisabledException extends ApiException {
    public EventDisabledException(String message) {
        super(HttpStatus.FORBIDDEN, message);
    }
}
