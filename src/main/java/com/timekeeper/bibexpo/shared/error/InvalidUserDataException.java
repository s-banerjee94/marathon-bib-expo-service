package com.timekeeper.bibexpo.shared.error;

import org.springframework.http.HttpStatus;

public class InvalidUserDataException extends ApiException {
    public InvalidUserDataException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
