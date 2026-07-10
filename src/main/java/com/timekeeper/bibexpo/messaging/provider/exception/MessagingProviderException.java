package com.timekeeper.bibexpo.messaging.provider.exception;

import com.timekeeper.bibexpo.exception.ApiException;
import org.springframework.http.HttpStatus;

/**
 * Raised when a system message cannot be sent: the channel's provider is missing, disabled, or the
 * provider call failed. Best-effort callers catch this and record a failed delivery.
 */
public class MessagingProviderException extends ApiException {

    public MessagingProviderException(String message) {
        super(HttpStatus.BAD_GATEWAY, message);
    }

    public MessagingProviderException(String message, Throwable cause) {
        super(HttpStatus.BAD_GATEWAY, message, cause);
    }
}
