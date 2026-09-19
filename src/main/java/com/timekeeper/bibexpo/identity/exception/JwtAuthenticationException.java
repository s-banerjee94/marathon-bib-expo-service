package com.timekeeper.bibexpo.identity.exception;

import com.timekeeper.bibexpo.shared.error.ApiException;
import org.springframework.http.HttpStatus;

public class JwtAuthenticationException extends ApiException {

    public JwtAuthenticationException(String message) {
        super(HttpStatus.UNAUTHORIZED, message);
    }

    public JwtAuthenticationException(String message, Throwable cause) {
        super(HttpStatus.UNAUTHORIZED, message, cause);
    }
}
