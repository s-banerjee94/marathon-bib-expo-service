package com.timekeeper.bibexpo.billing.repository;

import com.timekeeper.bibexpo.billing.model.entity.EventBillingState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Access to the per-event billing control row ({@link EventBillingState}).
 *
 * <p>The {@code claim*} methods are the quota gate. Each is a single conditional
 * {@code UPDATE}: it increments the counter only while the event is not finalized and
 * the quota is not yet spent, relying on the row's exclusive lock so concurrent
 * requests serialize. A return of {@code 1} means the slot was claimed; {@code 0}
 * means it was refused (quota exhausted or already finalized — the caller re-reads the
 * row to tell which).
 */
public interface EventBillingStateRepository extends JpaRepository<EventBillingState, Long> {

    /**
     * Creates the control row for an event if it does not already exist, race-safely
     * (a concurrent insert is ignored). Existing counters are left untouched, so the
     * quota persists across a reopen → re-complete cycle.
     *
     * @param eventId the event to ensure a row for
     * @return {@code 1} if a row was inserted, {@code 0} if one already existed
     */
    @Modifying
    @Query(value = "INSERT IGNORE INTO event_billing_state "
            + "(event_id, org_admin_attempts, admin_attempts, final_locked) "
            + "VALUES (:eventId, 0, 0, 0)", nativeQuery = true)
    int insertIfAbsent(@Param("eventId") Long eventId);

    /**
     * Atomically claims one ORGANIZER_ADMIN manual-request slot.
     *
     * @param eventId the event whose quota is being spent
     * @param max     the per-role quota ceiling
     * @return {@code 1} if a slot was claimed, {@code 0} if refused
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE EventBillingState s SET s.orgAdminAttempts = s.orgAdminAttempts + 1 "
            + "WHERE s.eventId = :eventId AND s.finalLocked = false AND s.orgAdminAttempts < :max")
    int claimOrgAdminSlot(@Param("eventId") Long eventId, @Param("max") int max);

    /**
     * Atomically claims one ROOT/ADMIN manual-request slot.
     *
     * @param eventId the event whose quota is being spent
     * @param max     the per-role quota ceiling
     * @return {@code 1} if a slot was claimed, {@code 0} if refused
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE EventBillingState s SET s.adminAttempts = s.adminAttempts + 1 "
            + "WHERE s.eventId = :eventId AND s.finalLocked = false AND s.adminAttempts < :max")
    int claimAdminSlot(@Param("eventId") Long eventId, @Param("max") int max);

    /**
     * Refunds one ORGANIZER_ADMIN slot — used when a claimed request fails before any
     * bill is produced (a hard Lambda/infrastructure failure, not a normal skip).
     *
     * @param eventId the event to refund
     * @return {@code 1} if a slot was refunded, {@code 0} if there was none to refund
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE EventBillingState s SET s.orgAdminAttempts = s.orgAdminAttempts - 1 "
            + "WHERE s.eventId = :eventId AND s.orgAdminAttempts > 0")
    int refundOrgAdminSlot(@Param("eventId") Long eventId);

    /**
     * Refunds one ROOT/ADMIN slot — used when a claimed request fails before any bill
     * is produced (a hard Lambda/infrastructure failure, not a normal skip).
     *
     * @param eventId the event to refund
     * @return {@code 1} if a slot was refunded, {@code 0} if there was none to refund
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE EventBillingState s SET s.adminAttempts = s.adminAttempts - 1 "
            + "WHERE s.eventId = :eventId AND s.adminAttempts > 0")
    int refundAdminSlot(@Param("eventId") Long eventId);
}
