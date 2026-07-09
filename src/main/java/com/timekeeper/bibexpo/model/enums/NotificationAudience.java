package com.timekeeper.bibexpo.model.enums;

/**
 * Who a notification is sent to. The resolver turns each value into a concrete list of recipient
 * users; the {@code ORGANIZATION_*} values additionally require an organization id, and
 * {@code USER} requires a target user id.
 */
public enum NotificationAudience {
    /** Every ROOT and ADMIN (the application owners). */
    PLATFORM_ADMINS,
    /** Every ROOT. */
    ROOT,
    /** Every ADMIN. */
    ADMIN,
    /** A single specific user. */
    USER,
    /** Everyone under an organization (org admins + org users + distributors). */
    ORGANIZATION_ALL,
    /** An organization's ORGANIZER_ADMIN users only. */
    ORGANIZATION_ADMINS,
    /** An organization's ORGANIZER_ADMIN + ORGANIZER_USER (no distributors). */
    ORGANIZATION_STAFF,
    /** An organization's DISTRIBUTOR users only. */
    ORGANIZATION_DISTRIBUTORS
}
