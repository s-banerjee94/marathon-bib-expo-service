package com.timekeeper.bibexpo.messaging.provider.exception;

/**
 * Raised when a system message cannot be sent: the channel's provider is missing, disabled, or the
 * provider call failed. Best-effort callers catch this and record a failed delivery.
 */
public class MessagingProviderException extends RuntimeException {

    public MessagingProviderException(String message) {
        super(message);
    }

    public MessagingProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
