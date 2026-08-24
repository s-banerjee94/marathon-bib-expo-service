package com.timekeeper.bibexpo.identity.service.impl;

import com.timekeeper.bibexpo.shared.cache.CacheNames;
import com.timekeeper.bibexpo.identity.config.JwtConfig;
import com.timekeeper.bibexpo.identity.model.entity.ActiveSession;
import com.timekeeper.bibexpo.identity.repository.ActiveSessionRepository;
import com.timekeeper.bibexpo.identity.service.SessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SessionServiceImpl implements SessionService {

    private final ActiveSessionRepository activeSessionRepository;
    private final JwtConfig jwtConfig;

    @Override
    @Transactional
    @CacheEvict(value = CacheNames.ACTIVE_SESSIONS_CACHE, key = "#username")
    public String startSession(String username, String deviceInfo) {
        String sid = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Instant expiresAt = now.plusMillis(jwtConfig.getRefreshTokenExpiration());

        activeSessionRepository.upsert(username, sid, expiresAt, now, deviceInfo);

        log.info("Session started for user {} (sid={})", username, sid);
        return sid;
    }

    @Override
    @Cacheable(value = CacheNames.ACTIVE_SESSIONS_CACHE, key = "#username", unless = "#result == null")
    public String getActiveSid(String username) {
        return activeSessionRepository.findByUsername(username)
                .filter(s -> s.getExpiresAt().isAfter(Instant.now()))
                .map(ActiveSession::getSid)
                .orElse(null);
    }

    @Override
    @Transactional
    public void extendSession(String username) {
        Instant expiresAt = Instant.now().plusMillis(jwtConfig.getRefreshTokenExpiration());
        activeSessionRepository.extendExpiry(username, expiresAt);
        log.debug("Session extended for user {}", username);
    }

    @Override
    @Transactional
    @CacheEvict(value = CacheNames.ACTIVE_SESSIONS_CACHE, key = "#username")
    public void endSession(String username) {
        activeSessionRepository.deleteByUsername(username);
        log.info("Session ended for user {}", username);
    }
}
