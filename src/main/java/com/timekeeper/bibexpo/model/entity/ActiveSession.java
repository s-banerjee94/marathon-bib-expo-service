package com.timekeeper.bibexpo.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "active_sessions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActiveSession {

    @Id
    @Column(name = "username", nullable = false, columnDefinition = "VARCHAR(255) COLLATE utf8mb4_bin")
    private String username;

    @Column(name = "sid", nullable = false, length = 36)
    private String sid;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "device_info", length = 500)
    private String deviceInfo;
}
