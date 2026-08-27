package com.timekeeper.bibexpo.identity.service.impl;

import com.timekeeper.bibexpo.identity.config.JwtConfig;
import com.timekeeper.bibexpo.identity.config.SessionConfig;
import com.timekeeper.bibexpo.identity.model.DeviceDetails;
import com.timekeeper.bibexpo.identity.model.dto.response.SessionResponse;
import com.timekeeper.bibexpo.identity.model.entity.ActiveSession;
import com.timekeeper.bibexpo.identity.repository.ActiveSessionRepository;
import com.timekeeper.bibexpo.identity.service.SessionService;
import com.timekeeper.bibexpo.shared.cache.CacheNames;
import com.timekeeper.bibexpo.shared.security.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SessionServiceImpl implements SessionService {

    private final ActiveSessionRepository activeSessionRepository;
    private final JwtConfig jwtConfig;
    private final SessionConfig sessionConfig;

    @Override
    @Transactional
    @CacheEvict(value = CacheNames.ACTIVE_SESSIONS_CACHE, key = "#username")
    public String startSession(String username, UserRole role, DeviceDetails device) {
        Instant now = Instant.now();
        ActiveSession session = ActiveSession.builder()
                .sid(UUID.randomUUID().toString())
                .username(username)
                .createdAt(now)
                .lastSeenAt(now)
                .expiresAt(now.plusMillis(jwtConfig.getRefreshTokenExpiration()))
                .ipAddress(device.getIpAddress())
                .browser(device.getBrowser())
                .operatingSystem(device.getOperatingSystem())
                .deviceType(device.getDeviceType())
                .userAgent(device.getUserAgent())
                .build();
        activeSessionRepository.save(session);

        evictBeyondLimit(username, sessionConfig.maxDevicesFor(role));

        log.info("Session started for user {} (sid={}, device={} on {} from {})",
                username, session.getSid(), device.getBrowser(), device.getOperatingSystem(),
                device.getIpAddress());
        return session.getSid();
    }

    @Override
    @Cacheable(value = CacheNames.ACTIVE_SESSIONS_CACHE, key = "#username")
    public Set<String> getActiveSids(String username) {
        return new HashSet<>(activeSessionRepository.findActiveSids(username, Instant.now()));
    }

    @Override
    @Transactional
    public void extendSession(String username, String sid) {
        Instant now = Instant.now();
        activeSessionRepository.touch(username, sid, now.plusMillis(jwtConfig.getRefreshTokenExpiration()), now);
        log.debug("Session extended for user {} (sid={})", username, sid);
    }

    @Override
    @Transactional
    @CacheEvict(value = CacheNames.ACTIVE_SESSIONS_CACHE, key = "#username")
    public boolean endSession(String username, String sid) {
        boolean ended = activeSessionRepository.deleteBySidAndUsername(username, sid) > 0;
        if (ended) {
            log.info("Session ended for user {} (sid={})", username, sid);
        }
        return ended;
    }

    @Override
    @Transactional
    @CacheEvict(value = CacheNames.ACTIVE_SESSIONS_CACHE, key = "#username")
    public int endOtherSessions(String username, String keepSid) {
        int ended = activeSessionRepository.deleteByUsernameExcept(username, keepSid);
        log.info("Signed out {} other device(s) for user {}", ended, username);
        return ended;
    }

    @Override
    @Transactional
    @CacheEvict(value = CacheNames.ACTIVE_SESSIONS_CACHE, key = "#username")
    public void endAllSessions(String username) {
        int ended = activeSessionRepository.deleteByUsername(username);
        log.info("All {} session(s) ended for user {}", ended, username);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SessionResponse> listSessions(String username, String currentSid) {
        return activeSessionRepository.findByUsernameOrderByLastSeenAtDesc(username).stream()
                .map(session -> toResponse(session, currentSid))
                .toList();
    }

    private SessionResponse toResponse(ActiveSession session, String currentSid) {
        return SessionResponse.builder()
                .sessionId(session.getSid())
                .ipAddress(session.getIpAddress())
                .browser(session.getBrowser())
                .operatingSystem(session.getOperatingSystem())
                .deviceType(session.getDeviceType())
                .userAgent(session.getUserAgent())
                .createdAt(session.getCreatedAt())
                .lastSeenAt(session.getLastSeenAt())
                .expiresAt(session.getExpiresAt())
                .current(session.getSid().equals(currentSid))
                .build();
    }

    /**
     * Trims the user back to their device limit, oldest-used first. Reading the rows and deleting
     * the tail is deliberate: it costs nothing at these sizes, and it repairs itself if two
     * simultaneous logins both slipped past the limit.
     */
    private void evictBeyondLimit(String username, int maxDevices) {
        List<ActiveSession> sessions = activeSessionRepository.findByUsernameOrderByLastSeenAtDesc(username);
        if (sessions.size() <= maxDevices) {
            return;
        }
        List<ActiveSession> evicted = sessions.subList(maxDevices, sessions.size());
        activeSessionRepository.deleteAll(evicted);
        log.info("Device limit of {} reached for user {} — signed out {} least recently used device(s)",
                maxDevices, username, evicted.size());
    }
}
