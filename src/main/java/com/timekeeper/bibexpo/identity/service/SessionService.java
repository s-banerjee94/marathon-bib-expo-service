package com.timekeeper.bibexpo.identity.service;

import com.timekeeper.bibexpo.identity.model.DeviceDetails;
import com.timekeeper.bibexpo.identity.model.dto.response.SessionResponse;
import com.timekeeper.bibexpo.shared.security.UserRole;

import java.util.List;
import java.util.Set;

/**
 * Manages multi-device user sessions backed by the {@code active_sessions} table.
 * Each signed-in device holds its own session id ({@code sid}); how many a user may hold at
 * once is a per-role limit from {@code session.max-devices}, and a login past that limit signs
 * out their least recently used device.
 * <p>
 * Every method is keyed by username as well as sid, which is all the {@code active_sessions}
 * row holds — nothing here needs the user record itself. Scoping the writes by username is also
 * what stops one account from ending another's session.
 */
public interface SessionService {

    /**
     * Starts a session for a newly authenticated device, evicting the user's least recently used
     * device when this login takes them past their role's limit.
     *
     * @param username the authenticated user's username
     * @param role     the user's role, which selects the device limit
     * @param device   the details of the device logging in, never null
     * @return the newly generated session id (UUID)
     */
    String startSession(String username, UserRole role, DeviceDetails device);

    /**
     * Returns every sid the user may currently authenticate with, empty when they hold none.
     * Cached for short windows to avoid hitting MySQL on every request.
     * <p>
     * Callers test membership themselves rather than going through a helper — this keeps the
     * cache lookup on the external call path (Spring AOP proxies only intercept calls that come
     * from outside the bean).
     */
    Set<String> getActiveSids(String username);

    /**
     * Extends one device's refresh window and marks it as just used. Used by the refresh-token
     * flow, so the least-recently-used eviction measures real activity rather than login age.
     */
    void extendSession(String username, String sid);

    /**
     * Ends one of the user's own sessions: deletes the row and evicts the cached sids, so the
     * next request carrying a token for it is rejected.
     *
     * @return true when a session was ended, false when the sid was not theirs or already gone
     */
    boolean endSession(String username, String sid);

    /**
     * Ends every session the user holds except the one making the request.
     *
     * @return how many devices were signed out
     */
    int endOtherSessions(String username, String keepSid);

    /**
     * Ends every session the user holds, on every device. Used when the account itself can no
     * longer be trusted — a disabled account, or a changed password.
     */
    void endAllSessions(String username);

    /**
     * The user's signed-in devices, most recently used first.
     *
     * @param currentSid the sid of the device asking, so the list can mark itself
     * @return one entry per signed-in device, never null
     */
    List<SessionResponse> listSessions(String username, String currentSid);
}
