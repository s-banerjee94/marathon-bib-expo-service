package com.timekeeper.bibexpo.whatsapp.exception;

public class WhatsAppCampaignNotFoundException extends RuntimeException {

    public WhatsAppCampaignNotFoundException() {
        super("The WhatsApp campaign was not found.");
    }
}
