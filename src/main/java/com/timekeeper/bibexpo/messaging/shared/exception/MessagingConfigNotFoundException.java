package com.timekeeper.bibexpo.messaging.shared.exception;

/**
 * Raised by the root management API when a requested provider or template row does not exist yet.
 * Mapped to 404 by the controller.
 */
public class MessagingConfigNotFoundException extends RuntimeException {

    public MessagingConfigNotFoundException(String message) {
        super(message);
    }
}
