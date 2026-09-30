package com.timekeeper.bibexpo.participant.exception;

import com.timekeeper.bibexpo.shared.error.ApiException;
import org.springframework.http.HttpStatus;

public class ParticipantChangedException extends ApiException {

    public static final String DEFAULT_MESSAGE = "This participant was just changed by someone else; reload and try again.";

    public ParticipantChangedException() {
        super(HttpStatus.CONFLICT, DEFAULT_MESSAGE);
    }
}
