package com.timekeeper.bibexpo.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

/**
 * One end-of-day snapshot of platform-wide cumulative counts, keyed by date.
 * Maintained going forward by the daily snapshot job and seeded for history from
 * entity createdAt timestamps so the growth chart is populated from day one.
 */
@Entity
@Table(name = "platform_daily_stats")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlatformDailyStats {

    @Id
    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(nullable = false)
    private int organizations;

    @Column(nullable = false)
    private int totalEvents;

    @Column(nullable = false)
    private int activeEvents;

    @Column(nullable = false)
    private int totalUsers;

    @Column(nullable = false)
    private int distinctCities;

    @Column(nullable = false)
    private Instant computedAt;
}
