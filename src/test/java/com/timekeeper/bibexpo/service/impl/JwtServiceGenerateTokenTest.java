package com.timekeeper.bibexpo.service.impl;

import com.timekeeper.bibexpo.model.entity.Organization;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.entity.UserRole;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceGenerateTokenTest extends JwtServiceTestBase {

    @Test
    @DisplayName("token contains expected core claims for a user without organization")
    void tokenContainsCoreClaimsForUserWithoutOrganization() {
        User user = user(5L, "jane.doe", UserRole.ADMIN, null);

        String token = jwtService.generateToken(user);

        assertThat(token).isNotNull();
        assertThat(jwtService.extractUsername(token)).isEqualTo("jane.doe");
        assertThat(jwtService.extractRole(token)).isEqualTo("ADMIN");
        assertThat(jwtService.extractUserId(token)).isEqualTo(5L);
        assertThat(jwtService.extractOrganizationId(token)).isNull();
    }

    @Test
    @DisplayName("token includes organizationId claim when user belongs to an organization")
    void tokenIncludesOrganizationId() {
        Organization org = org(77L);
        User user = user(3L, "org.user", UserRole.ORGANIZER_USER, org);

        String token = jwtService.generateToken(user);

        assertThat(jwtService.extractOrganizationId(token)).isEqualTo(77L);
    }

    @Test
    @DisplayName("token includes authorities claim")
    void tokenIncludesAuthoritiesClaim() {
        User user = user(1L, "alice", UserRole.ADMIN, null);

        Claims claims = jwtService.extractAllClaims(jwtService.generateToken(user));

        assertThat(claims.get("authorities")).isNotNull();
    }

    @Test
    @DisplayName("token includes email claim when user has email set")
    void tokenIncludesEmailWhenPresent() {
        User user = User.builder()
                .id(7L).username("alice").password("hashed").role(UserRole.ADMIN)
                .email("alice@test.com")
                .accountNonExpired(true).accountNonLocked(true)
                .credentialsNonExpired(true).enabled(true).deleted(false)
                .build();

        Claims claims = jwtService.extractAllClaims(jwtService.generateToken(user));

        assertThat(claims.get("email")).isEqualTo("alice@test.com");
    }

    @Test
    @DisplayName("token includes fullName claim when user has fullName set")
    void tokenIncludesFullNameWhenPresent() {
        User user = User.builder()
                .id(8L).username("bob").password("hashed").role(UserRole.ADMIN)
                .fullName("Bob Smith")
                .accountNonExpired(true).accountNonLocked(true)
                .credentialsNonExpired(true).enabled(true).deleted(false)
                .build();

        Claims claims = jwtService.extractAllClaims(jwtService.generateToken(user));

        assertThat(claims.get("fullName")).isEqualTo("Bob Smith");
    }
}
