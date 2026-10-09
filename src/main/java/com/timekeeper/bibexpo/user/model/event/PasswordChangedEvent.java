package com.timekeeper.bibexpo.user.model.event;

/**
 * Published whenever a user's password is replaced — by the user themselves, or through a reset
 * link — so every device still holding a session from the old password can be signed out.
 */
public record PasswordChangedEvent(String username) {}
