package com.timekeeper.bibexpo.passwordreset.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * A pending password-reset held in memory only. It points at the user whose password will be set
 * and records how the link was issued: {@code issuedBy} is the administrator's username for an
 * admin-initiated reset, or {@code null} when the user requested it themselves via forgot-password.
 * Per-instance and not persisted: a pending reset is lost on restart, and the link only works on the
 * instance that issued it.
 */
@Getter
@Builder
@AllArgsConstructor
public class PasswordResetToken {

    private final Long userId;

    /** Administrator who generated the link, or {@code null} for a self-service forgot-password request. */
    private final String issuedBy;

    private final Instant expiresAt;
}
