package com.timekeeper.bibexpo.user.model.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Published whenever a user's password is replaced — by the user themselves, or through a reset
 * link — so every device still holding a session from the old password can be signed out.
 */
@Getter
@AllArgsConstructor
public class PasswordChangedEvent {

    private final String username;
}
