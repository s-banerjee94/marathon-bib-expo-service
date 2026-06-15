package com.timekeeper.bibexpo.messaging.campaign.exception;

public class WhatsAppTemplateNotFoundException extends RuntimeException {

    public WhatsAppTemplateNotFoundException() {
        super("The WhatsApp template was not found.");
    }
}
