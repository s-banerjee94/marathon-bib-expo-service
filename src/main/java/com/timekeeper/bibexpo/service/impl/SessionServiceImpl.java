package com.timekeeper.bibexpo.service.impl;

import com.timekeeper.bibexpo.config.CacheConfig;
import com.timekeeper.bibexpo.config.JwtConfig;
import com.timekeeper.bibexpo.model.entity.ActiveSession;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.repository.ActiveSessionRepository;
import com.timekeeper.bibexpo.service.SessionService;
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
    @CacheEvict(value = CacheConfig.ACTIVE_SESSIONS_CACHE, key = "#user.username")
    public String startSession(User user, String deviceInfo) {
        String sid = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Instant expiresAt = now.plusMillis(jwtConfig.getRefreshTokenExpiration());

        activeSessionRepository.upsert(user.getUsername(), sid, expiresAt, now, deviceInfo);

        log.info("Session started for user {} (sid={})", user.getUsername(), sid);
        return sid;
    }

    @Override
    @Cacheable(value = CacheConfig.ACTIVE_SESSIONS_CACHE, key = "#username", unless = "#result == null")
    public String getActiveSid(String username) {
        return activeSessionRepository.findByUsername(username)
                .filter(s -> s.getExpiresAt().isAfter(Instant.now()))
                .map(ActiveSession::getSid)
                .orElse(null);
    }

    @Override
    @Transactional
    public void extendSession(User user) {
        Instant expiresAt = Instant.now().plusMillis(jwtConfig.getRefreshTokenExpiration());
        activeSessionRepository.extendExpiry(user.getUsername(), expiresAt);
        log.debug("Session extended for user {}", user.getUsername());
    }

    @Override
    @Transactional
    @CacheEvict(value = CacheConfig.ACTIVE_SESSIONS_CACHE, key = "#user.username")
    public void endSession(User user) {
        activeSessionRepository.deleteByUsername(user.getUsername());
        log.info("Session ended for user {}", user.getUsername());
    }

    @Override
    @Transactional
    @CacheEvict(value = CacheConfig.ACTIVE_SESSIONS_CACHE, key = "#username")
    public void endSession(String username) {
        activeSessionRepository.deleteByUsername(username);
        log.info("Session ended for user {}", username);
    }
}
