package com.timekeeper.bibexpo.exception;

import org.springframework.http.HttpStatus;

public class ParticipantNotFoundException extends ApiException {

    public static final String DEFAULT_MESSAGE = "The participant you requested does not exist.";

    public ParticipantNotFoundException() {
        super(HttpStatus.NOT_FOUND, DEFAULT_MESSAGE);
    }

}
