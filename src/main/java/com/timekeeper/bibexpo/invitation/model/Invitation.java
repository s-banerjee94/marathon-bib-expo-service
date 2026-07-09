package com.timekeeper.bibexpo.invitation.model;

import com.timekeeper.bibexpo.model.entity.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * A pending invite held in memory only. The role and organization are fixed when the
 * invite is issued and are the trusted source on acceptance — they never come from the
 * invitee's request, so the target role/organization cannot be tampered with.
 */
@Getter
@Builder
@AllArgsConstructor
public class Invitation {
    private final UserRole role;
    private final Long organizationId;
    private final Long eventId;
    private final String invitedBy;
    private final String recipientPhone;
    private final Instant expiresAt;
}
