package com.timekeeper.bibexpo.exception;

public class CsrfValidationException extends RuntimeException {
    public CsrfValidationException(String message) {
        super(message);
    }
}
