package com.timekeeper.bibexpo.invitation.exception;

/**
 * Thrown when an invite token is missing, expired, already used, or no longer valid.
 * Handled locally by the public invitation controller as a 404.
 */
public class InvitationInvalidException extends RuntimeException {
    public InvitationInvalidException(String message) {
        super(message);
    }
}
