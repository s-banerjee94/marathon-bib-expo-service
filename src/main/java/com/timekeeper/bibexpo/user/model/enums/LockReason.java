package com.timekeeper.bibexpo.user.model.enums;

/** Why an account is locked, so lifting an organization-wide lock never frees a deliberate one. */
public enum LockReason {
    /** Locked by a platform administrator on this account. */
    MANUAL,
    /** Locked because its organization was disabled. */
    ORGANIZATION_DISABLED
}
