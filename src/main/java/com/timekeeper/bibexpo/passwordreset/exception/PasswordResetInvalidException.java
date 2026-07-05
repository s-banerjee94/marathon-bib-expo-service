package com.timekeeper.bibexpo.passwordreset.exception;

/**
 * Thrown when a reset token is missing, expired, already used, or no longer resolves to a user.
 * Handled locally by the public password-reset controller as a 404.
 */
public class PasswordResetInvalidException extends RuntimeException {
    public PasswordResetInvalidException(String message) {
        super(message);
    }
}
