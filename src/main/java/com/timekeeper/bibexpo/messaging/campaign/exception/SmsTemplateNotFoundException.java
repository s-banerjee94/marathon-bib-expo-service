package com.timekeeper.bibexpo.messaging.campaign.exception;

import com.timekeeper.bibexpo.exception.ApiException;
import org.springframework.http.HttpStatus;

public class SmsTemplateNotFoundException extends ApiException {

    public static final String DEFAULT_MESSAGE = "The SMS template you requested does not exist.";

    public SmsTemplateNotFoundException() {
        super(HttpStatus.NOT_FOUND, DEFAULT_MESSAGE);
    }

    public SmsTemplateNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }
}
