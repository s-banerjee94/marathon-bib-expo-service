package com.timekeeper.bibexpo.invitation.store;

import com.github.benmanes.caffeine.cache.Cache;
import com.timekeeper.bibexpo.invitation.model.Invitation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * In-memory store for pending invites, backed by a Caffeine cache whose write-expiry
 * enforces the link lifetime. Per-instance and not persisted: a pending invite is lost
 * on restart, and the link only works on the instance that issued it.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InvitationStore {

    private static final int TOKEN_BYTES = 32;
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final Cache<String, Invitation> invitationCache;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Stores an invite under a freshly generated opaque token and returns that token.
     */
    public String issue(Invitation invitation) {
        String token = generateToken();
        invitationCache.put(token, invitation);
        log.debug("Issued invite for role {} (org {}) by {}",
                invitation.getRole(), invitation.getOrganizationId(), invitation.getInvitedBy());
        return token;
    }

    /**
     * Returns the invite without removing it, or null if it is missing or expired.
     */
    public Invitation peek(String token) {
        return token == null ? null : invitationCache.getIfPresent(token);
    }

    /**
     * Atomically removes and returns the invite, or null if it is missing or expired.
     * The atomic removal guarantees a token can only be accepted once.
     */
    public void consume(String token) {
        if (token != null) {
            invitationCache.asMap().remove(token);
        }
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return URL_ENCODER.encodeToString(bytes);
    }
}
