package com.timekeeper.bibexpo.messaging.campaign.exception;

public class WhatsAppCampaignAlreadyActiveException extends RuntimeException {

    public WhatsAppCampaignAlreadyActiveException(String message) {
        super(message);
    }
}
