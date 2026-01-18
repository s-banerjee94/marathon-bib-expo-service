package com.timekeeper.bibexpo.exception;

public class SmsTemplateAlreadyExistsException extends RuntimeException {
    public SmsTemplateAlreadyExistsException(String message) {
        super(message);
    }
}
