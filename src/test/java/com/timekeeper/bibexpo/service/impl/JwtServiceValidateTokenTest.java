package com.timekeeper.bibexpo.service.impl;

import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.entity.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceValidateTokenTest extends JwtServiceTestBase {

    @Test
    @DisplayName("returns true for a valid token and matching user")
    void returnsTrueForValidToken() {
        User user = user(1L, "alice", UserRole.ROOT, null);
        String token = jwtService.generateToken(user);

        assertThat(jwtService.isTokenValid(token, user)).isTrue();
    }

    @Test
    @DisplayName("returns false when token belongs to a different user")
    void returnsFalseForWrongUser() {
        User alice = user(1L, "alice", UserRole.ROOT, null);
        User bob = user(2L, "bob", UserRole.ADMIN, null);
        String token = jwtService.generateToken(alice);

        assertThat(jwtService.isTokenValid(token, bob)).isFalse();
    }

    @Test
    @DisplayName("returns false when the user account is disabled")
    void returnsFalseForDisabledUser() {
        User user = disabledUser(6L, "disabled");
        String token = jwtService.generateToken(user);

        assertThat(jwtService.isTokenValid(token, user)).isFalse();
    }

    @Test
    @DisplayName("returns false for a malformed token string")
    void returnsFalseForMalformedToken() {
        User user = user(1L, "alice", UserRole.ROOT, null);

        assertThat(jwtService.isTokenValid("not.a.jwt", user)).isFalse();
    }

    @Test
    @DisplayName("returns false when token is signed with a different secret key")
    void returnsFalseForTokenSignedWithDifferentSecret() {
        User user = user(1L, "alice", UserRole.ROOT, null);
        String token = foreignToken(user);

        assertThat(jwtService.isTokenValid(token, user)).isFalse();
    }

    @Test
    @DisplayName("returns false for an expired token")
    void returnsFalseForExpiredToken() {
        User user = user(1L, "alice", UserRole.ROOT, null);
        String token = expiredToken(user);

        assertThat(jwtService.isTokenValid(token, user)).isFalse();
    }
}
