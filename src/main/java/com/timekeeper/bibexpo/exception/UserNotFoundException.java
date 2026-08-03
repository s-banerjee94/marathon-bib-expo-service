package com.timekeeper.bibexpo.exception;

import com.timekeeper.bibexpo.shared.error.ApiException;
import org.springframework.http.HttpStatus;

public class UserNotFoundException extends ApiException {

    public static final String DEFAULT_MESSAGE = "The user you requested does not exist.";

    public UserNotFoundException() {
        super(HttpStatus.NOT_FOUND, DEFAULT_MESSAGE);
    }

}
