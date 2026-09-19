package com.timekeeper.bibexpo.user.api;

import com.timekeeper.bibexpo.user.model.entity.User;

/**
 * Resolves the security principal behind a username, for the authentication layer. Separate from
 * {@link UserDirectory} on purpose: the user returned here comes from the authentication cache with
 * its organization and event already loaded, because every authenticated request needs it and none
 * of them may pay for a database round trip. Ordinary reads belong on {@code UserDirectory}.
 */
public interface AuthUserDirectory {

    /**
     * Returns the user who signs in with the given username.
     *
     * @param username the username to resolve
     * @return the user, with organization and event loaded, or {@code null} when no account has that
     *         username — a miss is not cached, so unknown names cannot crowd the cache out
     */
    User findByUsername(String username);
}
