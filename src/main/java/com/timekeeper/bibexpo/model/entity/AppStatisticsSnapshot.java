package com.timekeeper.bibexpo.model.entity;

import com.timekeeper.bibexpo.model.enums.StatisticsScope;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "app_statistics_snapshot",
        uniqueConstraints = {
                // organization_id uses GLOBAL_SCOPE_SENTINEL (0) for GLOBAL rows — MySQL ignores NULL in unique indexes
                @UniqueConstraint(name = "uk_stats_scope_org", columnNames = {"scope", "organization_id"})
        },
        indexes = {
                @Index(name = "idx_stats_scope", columnList = "scope")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppStatisticsSnapshot {

    /** Sentinel used as organization_id for the GLOBAL scope row to satisfy the unique constraint in MySQL. */
    public static final long GLOBAL_SCOPE_SENTINEL = 0L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatisticsScope scope;

    /**
     * Uses GLOBAL_SCOPE_SENTINEL (0) for GLOBAL scope rows.
     * Holds the real organization ID for ORGANIZATION scope rows.
     */
    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    /**
     * JSON payload holding the computed stats.
     * Sectioned structure allows adding new stat categories (events, distribution)
     * without schema changes.
     */
    @Column(name = "snapshot_data", nullable = false, columnDefinition = "JSON")
    private String snapshotData;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime refreshedAt;

    @PrePersist
    private void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.refreshedAt = now;
    }
}
