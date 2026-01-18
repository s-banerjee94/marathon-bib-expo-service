package com.timekeeper.bibexpo.exception;

public class SmsTemplateNotFoundException extends RuntimeException {
    public SmsTemplateNotFoundException(String message) {
        super(message);
    }
}
