package com.timekeeper.bibexpo.exception;

import org.springframework.http.HttpStatus;

public class CategoryNotFoundException extends ApiException {

    public static final String DEFAULT_MESSAGE = "The category you requested does not exist.";

    public CategoryNotFoundException() {
        super(HttpStatus.NOT_FOUND, DEFAULT_MESSAGE);
    }

    public CategoryNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }
}
