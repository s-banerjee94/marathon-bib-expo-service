package com.timekeeper.bibexpo.demo.store;

import com.github.benmanes.caffeine.cache.Cache;
import com.timekeeper.bibexpo.demo.model.DemoSession;
import com.timekeeper.bibexpo.demo.model.DemoSessionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * In-memory store for landing-page demo sessions, backed by a Caffeine cache. Per-instance
 * and not persisted: sessions are lost on restart, which is fine — the desktop simply mints
 * a new one. Entries outlive their logical expiry (cache write-expiry is longer than the
 * session TTL) so a phone scanning a stale QR gets a clean "expired" answer, not "not found".
 */
@Component
@RequiredArgsConstructor
public class DemoSessionStore {

    private static final int CODE_BYTES = 16;
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final Cache<String, DemoSession> demoSessionCache;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Stores the session under a freshly generated 128-bit URL-safe code and returns that code.
     */
    public String issue(DemoSession session) {
        String code = generateCode();
        demoSessionCache.put(code, session);
        return code;
    }

    /**
     * Returns the session, or null if the code is unknown or the entry has been evicted.
     */
    public DemoSession peek(String code) {
        return code == null ? null : demoSessionCache.getIfPresent(code);
    }

    /**
     * Counts sessions that are still live (neither collected nor past their TTL),
     * used to enforce the global creation cap.
     */
    public long liveCount() {
        demoSessionCache.cleanUp();
        return demoSessionCache.asMap().values().stream()
                .filter(session -> session.getStatus() != DemoSessionStatus.COLLECTED && !session.isExpired())
                .count();
    }

    private String generateCode() {
        byte[] bytes = new byte[CODE_BYTES];
        secureRandom.nextBytes(bytes);
        return URL_ENCODER.encodeToString(bytes);
    }
}
