package com.timekeeper.bibexpo.service.impl;

import com.timekeeper.bibexpo.exception.JwtAuthenticationException;
import com.timekeeper.bibexpo.model.entity.Organization;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.entity.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceExtractClaimsTest extends JwtServiceTestBase {

    @Test
    @DisplayName("extractUsername returns the subject from token")
    void extractUsernameReturnsSubject() {
        User user = user(1L, "bob.smith", UserRole.DISTRIBUTOR, null);
        String token = jwtService.generateToken(user);

        assertThat(jwtService.extractUsername(token)).isEqualTo("bob.smith");
    }

    @Test
    @DisplayName("extractRole returns the role from token claims")
    void extractRoleReturnsRole() {
        User user = user(1L, "admin.user", UserRole.ORGANIZER_ADMIN, null);
        String token = jwtService.generateToken(user);

        assertThat(jwtService.extractRole(token)).isEqualTo("ORGANIZER_ADMIN");
    }

    @Test
    @DisplayName("extractOrganizationId returns null when user has no organization")
    void extractOrganizationIdReturnsNullWhenAbsent() {
        User user = user(1L, "root.user", UserRole.ROOT, null);
        String token = jwtService.generateToken(user);

        assertThat(jwtService.extractOrganizationId(token)).isNull();
    }

    @Test
    @DisplayName("extractOrganizationId returns correct organization ID")
    void extractOrganizationIdReturnsCorrectId() {
        Organization org = org(20L);
        User user = user(3L, "org.admin", UserRole.ORGANIZER_ADMIN, org);
        String token = jwtService.generateToken(user);

        assertThat(jwtService.extractOrganizationId(token)).isEqualTo(20L);
    }

    @Test
    @DisplayName("extractUserId returns correct user ID from token")
    void extractUserIdReturnsCorrectId() {
        User user = user(42L, "alice", UserRole.ADMIN, null);
        String token = jwtService.generateToken(user);

        assertThat(jwtService.extractUserId(token)).isEqualTo(42L);
    }

    @Test
    @DisplayName("extractAllClaims throws JwtAuthenticationException for a malformed token")
    void extractAllClaimsThrowsForMalformedToken() {
        assertThatThrownBy(() -> jwtService.extractAllClaims("malformed.token.value"))
                .isInstanceOf(JwtAuthenticationException.class);
    }

    @Test
    @DisplayName("extractAllClaims throws JwtAuthenticationException for token signed with different secret")
    void extractAllClaimsThrowsForInvalidSignature() {
        User user = user(1L, "alice", UserRole.ROOT, null);
        String token = foreignToken(user);

        assertThatThrownBy(() -> jwtService.extractAllClaims(token))
                .isInstanceOf(JwtAuthenticationException.class);
    }

    @Test
    @DisplayName("extractAllClaims throws JwtAuthenticationException for an expired token")
    void extractAllClaimsThrowsForExpiredToken() {
        User user = user(1L, "alice", UserRole.ROOT, null);
        String token = expiredToken(user);

        assertThatThrownBy(() -> jwtService.extractAllClaims(token))
                .isInstanceOf(JwtAuthenticationException.class)
                .hasMessageContaining("expired");
    }
}
