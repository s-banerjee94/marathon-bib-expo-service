package com.timekeeper.bibexpo.billing.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Per-event billing control row: the manual-request quotas and the one-final lock.
 *
 * <p>Kept separate from {@code Invoice} on purpose — invoices are immutable financial
 * snapshots written by the Lambda, whereas these counters are mutable operational
 * bookkeeping owned by the Spring app. The quota cannot be derived from invoice rows
 * because a draft overwrites the previous draft, so there is no per-request history.
 *
 * <p>{@code eventId} is the primary key, so exactly one row can exist per event.
 */
@Entity
@Table(name = "event_billing_state")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventBillingState {

    @Id
    @Column(name = "event_id")
    private Long eventId;

    /** Manual bill requests spent by ORGANIZER_ADMIN (always draft). */
    @Column(name = "org_admin_attempts", nullable = false)
    @Builder.Default
    private int orgAdminAttempts = 0;

    /** Manual bill requests spent by ROOT/ADMIN (draft or final). */
    @Column(name = "admin_attempts", nullable = false)
    @Builder.Default
    private int adminAttempts = 0;

    /** Set once a FINAL invoice exists; blocks every further bill request. */
    @Column(name = "final_locked", nullable = false)
    @Builder.Default
    private boolean finalLocked = false;
}
