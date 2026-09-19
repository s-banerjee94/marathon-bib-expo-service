package com.timekeeper.bibexpo.identity.model;

import com.timekeeper.bibexpo.identity.model.enums.DeviceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * The identifying details of the device a session was started from: where it connected from,
 * and what browser and operating system it reported. Produced once at login and stored on the
 * session row, so the device list can be rendered without re-parsing anything.
 */
@Getter
@Builder
@AllArgsConstructor
public class DeviceDetails {

    private final String ipAddress;
    private final String browser;
    private final String operatingSystem;
    private final DeviceType deviceType;
    private final String userAgent;
}
