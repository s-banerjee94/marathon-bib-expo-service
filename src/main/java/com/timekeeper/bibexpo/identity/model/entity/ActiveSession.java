package com.timekeeper.bibexpo.identity.model.entity;

import com.timekeeper.bibexpo.identity.model.enums.DeviceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "active_sessions", indexes = {
        @Index(name = "idx_active_sessions_username", columnList = "username")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActiveSession {

    @Id
    @Column(name = "sid", nullable = false, length = 36)
    private String sid;

    @Column(name = "username", nullable = false, columnDefinition = "VARCHAR(255) COLLATE utf8mb4_bin")
    private String username;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Last refresh of this device, which is what the least-recently-used eviction orders on. */
    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "browser", length = 100)
    private String browser;

    @Column(name = "operating_system", length = 100)
    private String operatingSystem;

    @Enumerated(EnumType.STRING)
    @Column(name = "device_type", length = 20)
    private DeviceType deviceType;

    @Column(name = "user_agent", length = 512)
    private String userAgent;
}
