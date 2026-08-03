package com.timekeeper.bibexpo.exception;

import com.timekeeper.bibexpo.shared.error.ApiException;
import org.springframework.http.HttpStatus;

public class OrganizationNotFoundException extends ApiException {

    public static final String DEFAULT_MESSAGE = "The organization you requested does not exist.";

    public OrganizationNotFoundException() {
        super(HttpStatus.NOT_FOUND, DEFAULT_MESSAGE);
    }

    public OrganizationNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }
}
