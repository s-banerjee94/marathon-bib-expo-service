package com.timekeeper.bibexpo.user.api;

/**
 * Creates the platform's root account at startup, for the composition root that owns the
 * credentials.
 *
 * <p>Root is the one account no actor creates: there is no organization to charge a seat to and no
 * signed-in user to authorize it, so it cannot go through the normal user-creation path. It is
 * still a user, though, and hashing its password and writing its row belong to the module that owns
 * users — bootstrap supplies the configured credentials and nothing else.
 */
public interface RootAccountProvisioner {

    /**
     * Creates the root account when the platform has none, and does nothing when it already exists.
     *
     * @param username    the configured root username
     * @param rawPassword the configured root password, hashed before it is stored
     */
    void ensureRootAccount(String username, String rawPassword);
}
