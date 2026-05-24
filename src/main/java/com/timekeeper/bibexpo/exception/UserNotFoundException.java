package com.timekeeper.bibexpo.exception;

public class UserNotFoundException extends RuntimeException {

    public static final String DEFAULT_MESSAGE = "The user you requested does not exist.";

    public UserNotFoundException() {
        super(DEFAULT_MESSAGE);
    }

}
