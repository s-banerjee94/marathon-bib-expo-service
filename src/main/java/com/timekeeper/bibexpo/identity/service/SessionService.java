package com.timekeeper.bibexpo.identity.service;

/**
 * Manages single-device user sessions backed by the {@code active_sessions} table.
 * Each user has at most one active session id ({@code sid}); a new login
 * overwrites any prior session atomically (single-device enforcement).
 * <p>
 * A session is keyed by username alone, which is all the {@code active_sessions}
 * row holds — nothing here needs the user record itself.
 */
public interface SessionService {

    /**
     * Starts a brand-new session for the user, overwriting any existing one.
     *
     * @param username   the authenticated user's username
     * @param deviceInfo optional User-Agent + IP string for diagnostics
     * @return the newly generated session id (UUID)
     */
    String startSession(String username, String deviceInfo);

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
    void extendSession(String username);

    /**
     * Ends the user's session: deletes the row and evicts the cached sid, so the
     * next request carrying a token for it is rejected.
     */
    void endSession(String username);
}
