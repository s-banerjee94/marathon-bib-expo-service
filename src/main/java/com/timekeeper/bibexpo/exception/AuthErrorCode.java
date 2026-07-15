package com.timekeeper.bibexpo.exception;

/**
 * Machine-readable codes returned in the {@code code} field of authentication 401 bodies.
 * The frontend branches on these values; the accompanying {@code message} is display text only,
 * so wording can change without breaking client behaviour.
 */
public final class AuthErrorCode {

    public static final String SESSION_INVALIDATED = "SESSION_INVALIDATED";
    public static final String ACCOUNT_LOCKED = "ACCOUNT_LOCKED";
    public static final String ACCOUNT_DISABLED = "ACCOUNT_DISABLED";

    private AuthErrorCode() {
    }
}
