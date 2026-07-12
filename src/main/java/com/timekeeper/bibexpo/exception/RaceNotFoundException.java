package com.timekeeper.bibexpo.exception;

import org.springframework.http.HttpStatus;

public class RaceNotFoundException extends ApiException {

    public static final String DEFAULT_MESSAGE = "The race you requested does not exist.";

    public RaceNotFoundException() {
        super(HttpStatus.NOT_FOUND, DEFAULT_MESSAGE);
    }

}
