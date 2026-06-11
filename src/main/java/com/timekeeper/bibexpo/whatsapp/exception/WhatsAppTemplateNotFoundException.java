package com.timekeeper.bibexpo.whatsapp.exception;

public class WhatsAppTemplateNotFoundException extends RuntimeException {

    public WhatsAppTemplateNotFoundException() {
        super("The WhatsApp template was not found.");
    }
}
