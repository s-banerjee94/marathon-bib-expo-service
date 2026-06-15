package com.timekeeper.bibexpo.messaging.campaign.exception;

public class SmsTemplateAlreadyExistsException extends RuntimeException {
    public SmsTemplateAlreadyExistsException(String message) {
        super(message);
    }
}
