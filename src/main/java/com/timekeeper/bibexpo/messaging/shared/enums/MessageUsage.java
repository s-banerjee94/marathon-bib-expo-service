package com.timekeeper.bibexpo.messaging.shared.enums;

/**
 * Why a messaging provider row exists: system-initiated transactional flows (OTP, invite, bill)
 * versus participant-facing expo campaigns. The two are configured separately so a campaign's
 * sender identity and reputation never mix with system mail. SYSTEM rows are always platform-wide
 * (no organization); CAMPAIGN rows may be a per-organization override of the platform default.
 */
public enum MessageUsage {
    SYSTEM,
    CAMPAIGN
}
