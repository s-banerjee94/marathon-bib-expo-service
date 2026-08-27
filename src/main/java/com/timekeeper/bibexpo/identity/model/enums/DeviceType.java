package com.timekeeper.bibexpo.identity.model.enums;

/**
 * The broad form factor a session was started from, derived from the User-Agent
 * and client hints. Lets the device list show the right icon per session.
 */
public enum DeviceType {
    DESKTOP,
    MOBILE,
    TABLET,
    UNKNOWN
}
