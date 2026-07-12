package com.timekeeper.bibexpo.messaging.shared.exception;

import com.timekeeper.bibexpo.exception.ApiException;
import org.springframework.http.HttpStatus;

/**
 * Raised by the root management API when a requested provider or template row does not exist yet.
 * Mapped to 404 via {@link com.timekeeper.bibexpo.exception.ApiException}.
 */
public class MessagingConfigNotFoundException extends ApiException {

    public MessagingConfigNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }
}
