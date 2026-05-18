package com.timekeeper.bibexpo.service;

import com.timekeeper.bibexpo.model.entity.User;

/**
 * Manages single-device user sessions backed by the {@code active_sessions} table.
 * Each user has at most one active session id ({@code sid}); a new login
 * overwrites any prior session atomically (single-device enforcement).
 */
public interface SessionService {

    /**
     * Starts a brand-new session for the user, overwriting any existing one.
     *
     * @param user       authenticated user
     * @param deviceInfo optional User-Agent + IP string for diagnostics
     * @return the newly generated session id (UUID)
     */
    String startSession(User user, String deviceInfo);

    /**
     * Returns the currently active sid for the user, or {@code null} if none.
     * Cached for short windows to avoid hitting MySQL on every request.
     * <p>
     * Callers compare the value themselves rather than going through a helper —
     * this keeps the cache lookup on the external call path (Spring AOP proxies
     * only intercept calls that come from outside the bean).
     */
    String getActiveSid(String username);

    /**
     * Extends the user's session expiry without changing the sid. Used by the
     * refresh-token flow so multiple tabs sharing the same refresh cookie
     * remain on the same sid and don't invalidate each other.
     */
    void extendSession(User user);

    /**
     * Ends the user's session: deletes the DB row, evicts the cache entry,
     * and closes any open SSE emitters for that user.
     */
    void endSession(User user);

    /**
     * Same as {@link #endSession(User)} but used when only the username is
     * available (e.g. forced logout from the JWT filter on sid mismatch).
     */
    void endSession(String username, Long userId);
}
