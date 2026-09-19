package com.timekeeper.bibexpo.identity.model.dto.response;

import com.timekeeper.bibexpo.identity.model.enums.DeviceType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
@AllArgsConstructor
@Schema(description = "One device the user is currently signed in on")
public class SessionResponse {

    @Schema(description = "Session id, used to sign this device out", example = "6f1c2f7a-8f0e-4a1e-9f2b-2c9a1d3e4b5c")
    private final String sessionId;

    @Schema(description = "Address the device signed in from", example = "203.0.113.9")
    private final String ipAddress;

    @Schema(description = "Browser and major version, null when it could not be identified", example = "Chrome 122")
    private final String browser;

    @Schema(description = "Operating system and version, null when it could not be identified", example = "Windows 10/11")
    private final String operatingSystem;

    @Schema(description = "Form factor of the device")
    private final DeviceType deviceType;

    @Schema(description = "Raw User-Agent the device reported, kept so an unrecognised client stays diagnosable")
    private final String userAgent;

    @Schema(description = "When this device signed in")
    private final Instant createdAt;

    @Schema(description = "When this device last refreshed its token — the order devices are evicted in")
    private final Instant lastSeenAt;

    @Schema(description = "When this device is signed out automatically unless it refreshes again")
    private final Instant expiresAt;

    @Schema(description = "True for the device making this request", example = "true")
    private final boolean current;
}
