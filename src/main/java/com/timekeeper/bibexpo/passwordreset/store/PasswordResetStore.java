package com.timekeeper.bibexpo.passwordreset.store;

import com.github.benmanes.caffeine.cache.Cache;
import com.timekeeper.bibexpo.passwordreset.model.PasswordResetToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * In-memory store for pending password resets, backed by a Caffeine cache whose write-expiry
 * enforces the link lifetime. Per-instance and not persisted: a pending reset is lost on restart,
 * and the link only works on the instance that issued it.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PasswordResetStore {

    private static final int TOKEN_BYTES = 32;
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final Cache<String, PasswordResetToken> passwordResetCache;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Stores a reset under a freshly generated opaque token and returns that token.
     */
    public String issue(PasswordResetToken token) {
        String value = generateToken();
        passwordResetCache.put(value, token);
        log.debug("Issued password reset for user {} (issuedBy {})", token.getUserId(), token.getIssuedBy());
        return value;
    }

    /**
     * Returns the reset without removing it, or null if it is missing or expired.
     */
    public PasswordResetToken peek(String token) {
        return token == null ? null : passwordResetCache.getIfPresent(token);
    }

    /**
     * Removes the reset so its token can only be completed once.
     */
    public void consume(String token) {
        if (token != null) {
            passwordResetCache.asMap().remove(token);
        }
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return URL_ENCODER.encodeToString(bytes);
    }
}
