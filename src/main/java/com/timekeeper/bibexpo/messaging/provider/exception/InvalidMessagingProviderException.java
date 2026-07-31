package com.timekeeper.bibexpo.messaging.provider.exception;

import com.timekeeper.bibexpo.exception.ApiException;
import org.springframework.http.HttpStatus;

public class InvalidMessagingProviderException extends ApiException {

    public InvalidMessagingProviderException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
