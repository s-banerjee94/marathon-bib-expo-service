package com.timekeeper.bibexpo.organization.api;

/**
 * The two organization-wide inventory caps and the reservation that enforces them. The caps and
 * counters belong to the organization; the inventory module reserves and releases as terms and
 * locations are created and removed, so this port is what keeps that dependency pointing one way.
 *
 * <p>The reservations return a boolean rather than throwing, because the message a user sees for a
 * full inventory belongs to the inventory module, not to this one.
 *
 * <p>The remaining inventory caps have no place here: options per attribute, variants per item and
 * variant attributes per item are ceilings on a single row, so their usage is counted on that row
 * and checked against {@link InventoryLimits}.
 */
public interface InventoryQuota {

    /**
     * Takes one term slot. The cap check and the increment are a single atomic update, so two
     * concurrent creates cannot both take the last remaining slot.
     *
     * @param organizationId the organization to reserve against
     * @return {@code true} when a slot was taken, {@code false} when the cap is already reached
     */
    boolean tryReserveTerm(Long organizationId);

    /**
     * Gives back a term slot as one is deleted. The decrement is floored at zero, so a release
     * with no matching reservation can never drive the counter negative.
     *
     * @param organizationId the organization to release against
     */
    void releaseTerm(Long organizationId);

    /**
     * Takes one location slot, atomically against the cap.
     *
     * @param organizationId the organization to reserve against
     * @return {@code true} when a slot was taken, {@code false} when the cap is already reached
     */
    boolean tryReserveLocation(Long organizationId);

    /**
     * Gives back a location slot as one is deleted, floored at zero.
     *
     * @param organizationId the organization to release against
     */
    void releaseLocation(Long organizationId);
}
