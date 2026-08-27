package com.timekeeper.bibexpo.identity.repository;

import com.timekeeper.bibexpo.identity.model.entity.ActiveSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * One row per signed-in device, keyed by session id ({@code sid}). A user may hold several rows
 * at once; how many is a per-role limit applied by the service, not by the schema.
 */
@Repository
public interface ActiveSessionRepository extends JpaRepository<ActiveSession, String> {

    /**
     * The user's devices, most recently used first — the order the eviction and the device
     * list both read.
     */
    List<ActiveSession> findByUsernameOrderByLastSeenAtDesc(String username);

    /**
     * The sids the user may currently authenticate with. Expired rows are excluded here rather
     * than relied on being purged, so a lapsed device stops working the moment it lapses.
     */
    @Query("SELECT s.sid FROM ActiveSession s WHERE s.username = :username AND s.expiresAt > :now")
    List<String> findActiveSids(@Param("username") String username, @Param("now") Instant now);

    /**
     * Extends one device's refresh window and marks it as just used. Scoped by username as well
     * as sid so a token for another account can never move someone else's row.
     */
    @Modifying
    @Query("""
            UPDATE ActiveSession s SET s.expiresAt = :expiresAt, s.lastSeenAt = :lastSeenAt
            WHERE s.sid = :sid AND s.username = :username
            """)
    int touch(@Param("username") String username,
              @Param("sid") String sid,
              @Param("expiresAt") Instant expiresAt,
              @Param("lastSeenAt") Instant lastSeenAt);

    /** Signs out one device. Username-scoped so a caller can only revoke their own session. */
    @Modifying
    @Query("DELETE FROM ActiveSession s WHERE s.sid = :sid AND s.username = :username")
    int deleteBySidAndUsername(@Param("username") String username, @Param("sid") String sid);

    /** Signs out every device except the one making the request. */
    @Modifying
    @Query("DELETE FROM ActiveSession s WHERE s.username = :username AND s.sid <> :keepSid")
    int deleteByUsernameExcept(@Param("username") String username, @Param("keepSid") String keepSid);

    @Modifying
    @Query("DELETE FROM ActiveSession s WHERE s.username = :username")
    int deleteByUsername(@Param("username") String username);

    @Modifying
    @Query("DELETE FROM ActiveSession s WHERE s.expiresAt < :now")
    int deleteAllExpired(@Param("now") Instant now);
}
