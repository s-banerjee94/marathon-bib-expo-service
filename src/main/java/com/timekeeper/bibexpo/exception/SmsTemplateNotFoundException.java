package com.timekeeper.bibexpo.exception;

public class SmsTemplateNotFoundException extends RuntimeException {

    public static final String DEFAULT_MESSAGE = "The SMS template you requested does not exist.";

    public SmsTemplateNotFoundException() {
        super(DEFAULT_MESSAGE);
    }

    public SmsTemplateNotFoundException(String message) {
        super(message);
    }
}
