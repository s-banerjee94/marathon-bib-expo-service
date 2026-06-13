package com.timekeeper.bibexpo.messaging.template.exception;

/**
 * Raised when no enabled system message template exists for a requested purpose and channel.
 * Best-effort delivery catches this and records a failed delivery for that channel.
 */
public class SystemTemplateNotFoundException extends RuntimeException {

    public SystemTemplateNotFoundException(String message) {
        super(message);
    }
}
