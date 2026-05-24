package com.timekeeper.bibexpo.exception;

public class SmsCampaignNotFoundException extends RuntimeException {

    public static final String DEFAULT_MESSAGE = "The SMS campaign you requested does not exist.";

    public SmsCampaignNotFoundException() {
        super(DEFAULT_MESSAGE);
    }

}
