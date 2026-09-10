package com.timekeeper.bibexpo.participant.exception;

import com.timekeeper.bibexpo.shared.error.ApiException;
import org.springframework.http.HttpStatus;

/**
 * Something still depends on the goody, so it cannot be removed: it has been handed out, or
 * another feature points at it. The message says which.
 */
public class EventGoodieInUseException extends ApiException {

    public static final String HANDED_OUT_MESSAGE = "You cannot remove a goody that has already been handed out.";

    public EventGoodieInUseException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
