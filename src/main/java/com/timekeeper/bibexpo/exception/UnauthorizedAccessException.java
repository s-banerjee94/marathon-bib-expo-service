package com.timekeeper.bibexpo.exception;

import org.springframework.http.HttpStatus;

public class UnauthorizedAccessException extends ApiException {
    public UnauthorizedAccessException(String message) {
        super(HttpStatus.FORBIDDEN, message);
    }
}
