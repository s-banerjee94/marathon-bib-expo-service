package com.timekeeper.bibexpo.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base class for every exception that maps directly to an HTTP error response. Subclasses declare
 * their status (and optionally a custom error label) once, and {@link GlobalExceptionHandler}
 * renders all of them through a single handler, so no per-exception handler is needed.
 */
@Getter
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String error;

    protected ApiException(HttpStatus status, String message) {
        this(status, status.getReasonPhrase(), message, null);
    }

    protected ApiException(HttpStatus status, String message, Throwable cause) {
        this(status, status.getReasonPhrase(), message, cause);
    }

    protected ApiException(HttpStatus status, String error, String message) {
        this(status, error, message, null);
    }

    protected ApiException(HttpStatus status, String error, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.error = error;
    }
}
