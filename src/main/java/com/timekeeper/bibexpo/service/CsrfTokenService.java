package com.timekeeper.bibexpo.service;

/**
 * Stateless double-submit CSRF token utility.
 * Server generates a 256-bit random token, sets it in a non-HttpOnly cookie,
 * and requires the same value to be echoed back in an {@code X-CSRF-Token}
 * header on state-changing cookie endpoints.
 */
public interface CsrfTokenService {

    /**
     * Generates a fresh CSRF token (256 bits, hex-encoded).
     */
    String generate();

    /**
     * Constant-time comparison of the value from the request header against
     * the value from the cookie. Returns {@code false} if either side is
     * null/blank.
     */
    boolean matches(String headerValue, String cookieValue);
}
