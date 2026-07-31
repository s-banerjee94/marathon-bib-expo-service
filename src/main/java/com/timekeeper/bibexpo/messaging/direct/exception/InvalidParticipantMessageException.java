package com.timekeeper.bibexpo.messaging.direct.exception;

import com.timekeeper.bibexpo.exception.ApiException;
import org.springframework.http.HttpStatus;

public class InvalidParticipantMessageException extends ApiException {

    public InvalidParticipantMessageException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
