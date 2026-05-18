package com.timekeeper.bibexpo.repository;

import com.timekeeper.bibexpo.model.entity.ActiveSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

/**
 * Single-row-per-user session store. The {@code username} PK enforces
 * single-device login: a new login overwrites any prior session atomically.
 */
@Repository
public interface ActiveSessionRepository extends JpaRepository<ActiveSession, String> {

    /**
     * Atomic upsert. MySQL-specific {@code ON DUPLICATE KEY UPDATE} guarantees
     * a concurrent second login cannot leave two rows for the same user.
     */
    @Modifying
    @Query(value = """
            INSERT INTO active_sessions (username, sid, expires_at, created_at, device_info)
            VALUES (:username, :sid, :expiresAt, :createdAt, :deviceInfo)
            ON DUPLICATE KEY UPDATE
              sid = VALUES(sid),
              expires_at = VALUES(expires_at),
              created_at = VALUES(created_at),
              device_info = VALUES(device_info)
            """, nativeQuery = true)
    void upsert(@Param("username") String username,
                @Param("sid") String sid,
                @Param("expiresAt") Instant expiresAt,
                @Param("createdAt") Instant createdAt,
                @Param("deviceInfo") String deviceInfo);

    Optional<ActiveSession> findByUsername(String username);

    /**
     * Extends the session expiry without touching the sid. Used by the refresh
     * flow so that multiple tabs sharing the refresh cookie stay on the same sid.
     */
    @Modifying
    @Query("UPDATE ActiveSession s SET s.expiresAt = :expiresAt WHERE s.username = :username")
    int extendExpiry(@Param("username") String username, @Param("expiresAt") Instant expiresAt);

    @Modifying
    @Query("DELETE FROM ActiveSession s WHERE s.username = :username")
    int deleteByUsername(@Param("username") String username);

    @Modifying
    @Query("DELETE FROM ActiveSession s WHERE s.expiresAt < :now")
    int deleteAllExpired(@Param("now") Instant now);
}
