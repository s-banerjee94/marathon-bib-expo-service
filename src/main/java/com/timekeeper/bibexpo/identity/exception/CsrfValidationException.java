package com.timekeeper.bibexpo.identity.exception;

public class CsrfValidationException extends RuntimeException {
    public CsrfValidationException(String message) {
        super(message);
    }
}
