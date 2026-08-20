package com.timekeeper.bibexpo.identity.service;

import com.timekeeper.bibexpo.user.model.entity.User;
import org.springframework.security.core.userdetails.UserDetails;

public interface JwtService {

    String TYPE_ACCESS = "access";
    String TYPE_REFRESH = "refresh";

    /**
     * Generate a short-lived access token (JWT) bound to the given session id.
     *
     * @param user authenticated user
     * @param sid  active session id (UUID) — embedded as the {@code sid} claim
     * @return access JWT
     */
    String generateAccessToken(User user, String sid);

    /**
     * Generate a long-lived refresh token (JWT) bound to the given session id.
     * Carries minimal claims: {@code sub}, {@code sid}, {@code type=refresh}.
     *
     * @param user authenticated user
     * @param sid  active session id (UUID)
     * @return refresh JWT
     */
    String generateRefreshToken(User user, String sid);

    /**
     * Extract username (subject) from a token.
     */
    String extractUsername(String token);

    /**
     * Validate the token itself: signature, expiry, type, and subject. Account status
     * (enabled, non-locked) is enforced separately by the security layer's UserDetailsChecker.
     *
     * @param expectedType either {@link #TYPE_ACCESS} or {@link #TYPE_REFRESH}
     */
    boolean isTokenValid(String token, UserDetails userDetails, String expectedType);

    /**
     * Extract sid (session id) claim.
     */
    String extractSid(String token);

    /**
     * Extract token type claim ({@code access} or {@code refresh}).
     */
    String extractTokenType(String token);

    /**
     * Access-token expiration in milliseconds, for the login response body.
     */
    long getAccessTokenExpirationMs();

    /**
     * Refresh-token expiration in milliseconds, for cookie {@code Max-Age}.
     */
    long getRefreshTokenExpirationMs();
}
