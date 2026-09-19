package com.timekeeper.bibexpo.participant.exception;

import com.timekeeper.bibexpo.shared.error.ApiException;
import org.springframework.http.HttpStatus;

public class EventGoodieAlreadyExistsException extends ApiException {

    public static final String DEFAULT_MESSAGE = "This event already has a goody with that name.";

    public EventGoodieAlreadyExistsException() {
        super(HttpStatus.CONFLICT, DEFAULT_MESSAGE);
    }
}
