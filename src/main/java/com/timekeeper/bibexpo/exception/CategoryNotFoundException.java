package com.timekeeper.bibexpo.exception;

public class CategoryNotFoundException extends RuntimeException {

    public static final String DEFAULT_MESSAGE = "The category you requested does not exist.";

    public CategoryNotFoundException() {
        super(DEFAULT_MESSAGE);
    }

    public CategoryNotFoundException(String message) {
        super(message);
    }
}
