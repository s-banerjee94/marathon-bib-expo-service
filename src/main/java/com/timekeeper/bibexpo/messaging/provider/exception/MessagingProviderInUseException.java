package com.timekeeper.bibexpo.messaging.provider.exception;

import com.timekeeper.bibexpo.exception.ApiException;
import org.springframework.http.HttpStatus;

public class MessagingProviderInUseException extends ApiException {

    public MessagingProviderInUseException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
