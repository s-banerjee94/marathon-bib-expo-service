package com.timekeeper.bibexpo.service.impl;

import com.timekeeper.bibexpo.config.JwtConfig;
import com.timekeeper.bibexpo.model.entity.Organization;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.entity.UserRole;

abstract class JwtServiceTestBase {

    static final String SECRET =
            "test-secret-key-for-unit-testing-only-must-be-at-least-256-bits-long-xxxx";
    static final long EXPIRATION = 604800000L;
    static final String ISSUER = "test-issuer";
    private static final String OTHER_SECRET =
            "different-secret-key-for-unit-testing-only-must-be-256-bits-long-yyyy";

    final JwtServiceImpl jwtService = buildService(EXPIRATION);

    JwtServiceImpl buildService(long expiration) {
        JwtConfig config = new JwtConfig();
        config.setSecret(SECRET);
        config.setExpiration(expiration);
        config.setIssuer(ISSUER);
        return new JwtServiceImpl(config);
    }

    Organization org(long id) {
        return Organization.builder()
                .id(id).organizerName("Test Org").email("org@test.com")
                .build();
    }

    User user(long id, String username, UserRole role, Organization org) {
        return User.builder()
                .id(id).username(username).password("hashed").role(role).organization(org)
                .accountNonExpired(true).accountNonLocked(true)
                .credentialsNonExpired(true).enabled(true).deleted(false)
                .build();
    }

    User disabledUser(long id, String username) {
        return User.builder()
                .id(id).username(username).password("hashed").role(UserRole.ORGANIZER_USER)
                .accountNonExpired(true).accountNonLocked(true)
                .credentialsNonExpired(true).enabled(false).deleted(false)
                .build();
    }

    String expiredToken(User user) {
        return buildService(-1000000L).generateToken(user);
    }

    String foreignToken(User user) {
        JwtConfig config = new JwtConfig();
        config.setSecret(OTHER_SECRET);
        config.setExpiration(EXPIRATION);
        config.setIssuer(ISSUER);
        return new JwtServiceImpl(config).generateToken(user);
    }
}
