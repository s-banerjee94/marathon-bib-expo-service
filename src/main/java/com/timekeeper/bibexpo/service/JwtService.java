package com.timekeeper.bibexpo.service;

import com.timekeeper.bibexpo.model.entity.User;
import io.jsonwebtoken.Claims;
import org.springframework.security.core.userdetails.UserDetails;

public interface JwtService {

    String TYPE_ACCESS = "access";
    String TYPE_REFRESH = "refresh";
    String TYPE_MCP = "mcp";

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
     * Generate a short-lived token scoped to the MCP server, used by the Python agent to call
     * tools on behalf of {@code user}. Carries {@code sub}, {@code userId}, {@code role} and
     * {@code type=mcp}, with no {@code sid} — it is a per-turn service credential, not a browser
     * session. The MCP security filter accepts only {@code type=mcp}; the REST chain rejects it.
     *
     * @param user the user the agent acts on behalf of
     * @return MCP-scoped JWT
     */
    String generateAgentToken(User user);

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
     * Extract all claims from a token. Throws {@code JwtAuthenticationException}
     * on parse or signature errors.
     */
    Claims extractAllClaims(String token);

    /**
     * Extract role claim.
     */
    String extractRole(String token);

    /**
     * Extract organizationId claim (may be null for system-level roles).
     */
    Long extractOrganizationId(String token);

    /**
     * Extract userId claim.
     */
    Long extractUserId(String token);

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
