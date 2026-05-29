package com.timekeeper.bibexpo.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "org_daily_stats")
@IdClass(OrgDailyStatsId.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrgDailyStats {

    @Id
    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Id
    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

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
