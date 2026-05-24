package com.timekeeper.bibexpo.exception;

public class ParticipantNotFoundException extends RuntimeException {

    public static final String DEFAULT_MESSAGE = "The participant you requested does not exist.";

    public ParticipantNotFoundException() {
        super(DEFAULT_MESSAGE);
    }

}
