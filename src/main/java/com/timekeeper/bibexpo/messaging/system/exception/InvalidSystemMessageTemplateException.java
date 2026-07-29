package com.timekeeper.bibexpo.messaging.system.exception;

import com.timekeeper.bibexpo.exception.ApiException;
import org.springframework.http.HttpStatus;

/**
 * Raised when a system message template's content cannot carry its message — an unknown placeholder,
 * or a template that omits the link or code the purpose exists to deliver. Rejected on save, and
 * again at send time so a row registered before that check fails visibly instead of dispatching a
 * message with a blank where the link belongs.
 */
public class InvalidSystemMessageTemplateException extends ApiException {

    public InvalidSystemMessageTemplateException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
