package com.timekeeper.bibexpo.messaging.shared.enums;

/**
 * System-initiated message flows that use an app-default template. Only INVITE is wired into a
 * sending flow now; PASSWORD_RESET and OTP have seeded templates and are the planned next consumers
 * of the same registry. BILL/GENERAL can be added later without structural change.
 */
public enum SystemTemplatePurpose {
    INVITE,
    PASSWORD_RESET,
    OTP
}
