package com.timekeeper.bibexpo.exception;

public class RaceNotFoundException extends RuntimeException {

    public static final String DEFAULT_MESSAGE = "The race you requested does not exist.";

    public RaceNotFoundException() {
        super(DEFAULT_MESSAGE);
    }

}
