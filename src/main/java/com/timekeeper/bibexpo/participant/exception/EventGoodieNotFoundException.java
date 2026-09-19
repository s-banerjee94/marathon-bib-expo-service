package com.timekeeper.bibexpo.participant.exception;

import com.timekeeper.bibexpo.shared.error.ApiException;
import org.springframework.http.HttpStatus;

public class EventGoodieNotFoundException extends ApiException {

    public static final String DEFAULT_MESSAGE = "The goody you requested does not exist.";

    public EventGoodieNotFoundException() {
        super(HttpStatus.NOT_FOUND, DEFAULT_MESSAGE);
    }
}
