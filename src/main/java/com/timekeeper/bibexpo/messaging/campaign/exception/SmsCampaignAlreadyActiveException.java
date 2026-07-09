package com.timekeeper.bibexpo.messaging.campaign.exception;

public class SmsCampaignAlreadyActiveException extends RuntimeException {

    public SmsCampaignAlreadyActiveException(String message) {
        super(message);
    }
}
