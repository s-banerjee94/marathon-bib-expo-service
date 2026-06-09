package com.timekeeper.bibexpo.billing.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * A single precomputed billing-statistics blob, served by the bill-stats read endpoint.
 *
 * <p>Mirrors {@code app_statistics_snapshot}: one row per scope holds a JSON payload computed
 * <em>entirely outside Spring</em> by the dedicated billing-stats Lambda. The Spring app is a
 * read-only consumer — it never writes this table. The Lambda upserts the GLOBAL row
 * ({@code scope = GLOBAL}, {@code scope_key = }{@link #GLOBAL_SCOPE_SENTINEL}) on every finalize,
 * payment toggle, or manual refresh. The {@code scope}/{@code scope_key} columns leave room for
 * per-organization snapshots later without a schema change.
 */
@Entity
@Table(name = "billing_stats_snapshot",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_billing_stats_scope", columnNames = {"scope", "scope_key"})
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillingStatsSnapshot {

    /** Scope value for the single platform-wide row. */
    public static final String SCOPE_GLOBAL = "GLOBAL";

    /** Sentinel used as scope_key for the GLOBAL row to satisfy the unique constraint in MySQL. */
    public static final long GLOBAL_SCOPE_SENTINEL = 0L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String scope;

    @Column(name = "scope_key", nullable = false)
    private Long scopeKey;

    /** The precomputed stats blob (currency + all three range windows). */
    @Column(name = "snapshot_data", nullable = false, columnDefinition = "JSON")
    private String snapshotData;

    /** What last triggered the recompute — FINALIZE, PAYMENT or MANUAL. */
    @Column(name = "computed_by", length = 16)
    private String computedBy;

    /** When the Lambda last computed this snapshot. */
    @Column(name = "refreshed_at", nullable = false)
    private Instant refreshedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
