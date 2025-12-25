package com.timekeeper.bibexpo.service;

import com.timekeeper.bibexpo.model.entity.User;
import io.jsonwebtoken.Claims;
import org.springframework.security.core.userdetails.UserDetails;

public interface JwtService {

    /**
     * Generate JWT token for a user
     * @param user User entity
     * @return JWT token string
     */
    String generateToken(User user);

    /**
     * Extract username from JWT token
     * @param token JWT token
     * @return username
     */
    String extractUsername(String token);

    /**
     * Validate JWT token against user details
     * @param token JWT token
     * @param userDetails User details
     * @return true if token is valid
     */
    boolean isTokenValid(String token, UserDetails userDetails);

    /**
     * Extract all claims from JWT token
     * @param token JWT token
     * @return Claims object
     */
    Claims extractAllClaims(String token);

    /**
     * Extract role from JWT token
     * @param token JWT token
     * @return role string
     */
    String extractRole(String token);

    /**
     * Extract organization ID from JWT token
     * @param token JWT token
     * @return organization ID or null
     */
    Long extractOrganizationId(String token);

    /**
     * Extract user ID from JWT token
     * @param token JWT token
     * @return user ID
     */
    Long extractUserId(String token);
}
