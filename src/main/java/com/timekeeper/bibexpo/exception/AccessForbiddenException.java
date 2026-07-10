package com.timekeeper.bibexpo.exception;

import org.springframework.http.HttpStatus;

public class AccessForbiddenException extends ApiException {
    public AccessForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, message);
    }
}
