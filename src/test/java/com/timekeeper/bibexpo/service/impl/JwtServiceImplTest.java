package com.timekeeper.bibexpo.service.impl;

import com.timekeeper.bibexpo.config.JwtConfig;
import com.timekeeper.bibexpo.exception.JwtAuthenticationException;
import com.timekeeper.bibexpo.model.entity.Organization;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.entity.UserRole;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceImplTest {

    private static final String SECRET =
            "test-secret-key-for-unit-testing-only-must-be-at-least-256-bits-long-xxxx";
    private static final long EXPIRATION = 604800000L;
    private static final String ISSUER = "test-issuer";

    private JwtServiceImpl jwtService;

    @BeforeEach
    void setUp() {
        JwtConfig config = new JwtConfig();
        config.setSecret(SECRET);
        config.setExpiration(EXPIRATION);
        config.setIssuer(ISSUER);
        jwtService = new JwtServiceImpl(config);
    }

    private User buildUser(Long id, String username, UserRole role, Organization org) {
        return User.builder()
                .id(id).username(username).password("hashed").role(role).organization(org)
                .accountNonExpired(true).accountNonLocked(true)
                .credentialsNonExpired(true).enabled(true).deleted(false)
                .build();
    }

    @Test
    void generateTokenContainsExpectedClaims() {
        User user = buildUser(5L, "jane.doe", UserRole.ADMIN, null);

        String token = jwtService.generateToken(user);

        assertNotNull(token);
        assertEquals("jane.doe", jwtService.extractUsername(token));
        assertEquals("ADMIN", jwtService.extractRole(token));
        assertEquals(5L, jwtService.extractUserId(token));
        assertNull(jwtService.extractOrganizationId(token));
    }

    @Test
    void generateTokenIncludesOrganizationId() {
        Organization org = Organization.builder().id(77L).organizerName("Org").email("e@e.com").build();
        User user = buildUser(3L, "org.user", UserRole.ORGANIZER_USER, org);

        String token = jwtService.generateToken(user);

        assertEquals(77L, jwtService.extractOrganizationId(token));
    }

    @Test
    void generateTokenIncludesAuthoritiesClaim() {
        User user = buildUser(1L, "alice", UserRole.ADMIN, null);

        String token = jwtService.generateToken(user);
        Claims claims = jwtService.extractAllClaims(token);

        assertNotNull(claims.get("authorities"));
    }

    @Test
    void isTokenValidReturnsTrueForValidToken() {
        User user = buildUser(1L, "alice", UserRole.ROOT, null);
        String token = jwtService.generateToken(user);

        assertTrue(jwtService.isTokenValid(token, user));
    }

    @Test
    void isTokenValidReturnsFalseForWrongUser() {
        User alice = buildUser(1L, "alice", UserRole.ROOT, null);
        User bob = buildUser(2L, "bob", UserRole.ADMIN, null);

        String token = jwtService.generateToken(alice);

        assertFalse(jwtService.isTokenValid(token, bob));
    }

    @Test
    void isTokenValidReturnsFalseForDisabledUser() {
        User user = User.builder()
                .id(6L).username("disabled").password("hashed").role(UserRole.ORGANIZER_USER)
                .accountNonExpired(true).accountNonLocked(true).credentialsNonExpired(true)
                .enabled(false).deleted(false)
                .build();
        String token = jwtService.generateToken(user);

        assertFalse(jwtService.isTokenValid(token, user));
    }

    @Test
    void isTokenValidReturnsFalseForMalformedToken() {
        User user = buildUser(1L, "alice", UserRole.ROOT, null);

        assertFalse(jwtService.isTokenValid("not.a.jwt", user));
    }

    @Test
    void isTokenValidReturnsFalseForTokenSignedWithDifferentSecret() {
        JwtConfig otherConfig = new JwtConfig();
        otherConfig.setSecret("different-secret-key-for-unit-testing-only-must-be-256-bits-long-yyyy");
        otherConfig.setExpiration(EXPIRATION);
        otherConfig.setIssuer(ISSUER);
        JwtServiceImpl otherService = new JwtServiceImpl(otherConfig);

        User user = buildUser(1L, "alice", UserRole.ROOT, null);
        String foreignToken = otherService.generateToken(user);

        assertFalse(jwtService.isTokenValid(foreignToken, user));
    }

    @Test
    void extractUsernameReturnsCorrectSubject() {
        User user = buildUser(1L, "bob.smith", UserRole.DISTRIBUTOR, null);
        String token = jwtService.generateToken(user);

        assertEquals("bob.smith", jwtService.extractUsername(token));
    }

    @Test
    void extractRoleReturnsCorrectRole() {
        User user = buildUser(1L, "admin.user", UserRole.ORGANIZER_ADMIN, null);
        String token = jwtService.generateToken(user);

        assertEquals("ORGANIZER_ADMIN", jwtService.extractRole(token));
    }

    @Test
    void extractOrganizationIdReturnsNullWhenAbsent() {
        User user = buildUser(1L, "root.user", UserRole.ROOT, null);
        String token = jwtService.generateToken(user);

        assertNull(jwtService.extractOrganizationId(token));
    }

    @Test
    void extractUserIdReturnsCorrectId() {
        User user = buildUser(42L, "alice", UserRole.ADMIN, null);
        String token = jwtService.generateToken(user);

        assertEquals(42L, jwtService.extractUserId(token));
    }

    @Test
    void extractAllClaimsThrowsForMalformedToken() {
        assertThrows(JwtAuthenticationException.class,
                () -> jwtService.extractAllClaims("malformed.token.value"));
    }

    @Test
    void extractAllClaimsThrowsForInvalidSignature() {
        JwtConfig otherConfig = new JwtConfig();
        otherConfig.setSecret("different-secret-key-for-unit-testing-only-must-be-256-bits-long-yyyy");
        otherConfig.setExpiration(EXPIRATION);
        otherConfig.setIssuer(ISSUER);
        JwtServiceImpl otherService = new JwtServiceImpl(otherConfig);

        User user = buildUser(1L, "alice", UserRole.ROOT, null);
        String foreignToken = otherService.generateToken(user);

        assertThrows(JwtAuthenticationException.class, () -> jwtService.extractAllClaims(foreignToken));
    }
}
