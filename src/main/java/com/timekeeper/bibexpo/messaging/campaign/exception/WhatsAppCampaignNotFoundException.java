package com.timekeeper.bibexpo.messaging.campaign.exception;

public class WhatsAppCampaignNotFoundException extends RuntimeException {

    public WhatsAppCampaignNotFoundException() {
        super("The WhatsApp campaign was not found.");
    }
}
