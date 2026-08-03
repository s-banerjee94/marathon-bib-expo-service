package com.timekeeper.bibexpo.messaging.campaign.exception;

import com.timekeeper.bibexpo.shared.error.ApiException;
import org.springframework.http.HttpStatus;

public class WhatsAppTemplateNotFoundException extends ApiException {

    public WhatsAppTemplateNotFoundException() {
        super(HttpStatus.NOT_FOUND, "The WhatsApp template was not found.");
    }
}
