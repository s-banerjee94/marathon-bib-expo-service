package com.timekeeper.bibexpo.exception;

public class OrganizationNotFoundException extends RuntimeException {

    public static final String DEFAULT_MESSAGE = "The organization you requested does not exist.";

    public OrganizationNotFoundException() {
        super(DEFAULT_MESSAGE);
    }

    public OrganizationNotFoundException(String message) {
        super(message);
    }
}
