package com.timekeeper.bibexpo.event.api;

/**
 * The per-event resource ceilings, for the modules that have to respect them.
 *
 * <p>Participants, races, categories, templates, campaigns and imports all live in other modules,
 * and each one checked its own count against {@code EventLimitRepository} directly. The limits are
 * the event's to publish; the count, and what to do when it is reached, stay with the module that
 * owns the resource.
 */
public interface EventQuota {

    /**
     * Returns the limits in force for an event.
     *
     * @param eventId the event
     * @return the event's limits, or the platform defaults when the event has no limits row
     */
    EventLimits forEvent(Long eventId);

    /**
     * Records that a full import finished successfully, spending one of the event's import
     * allowance. Called once the run completes, so a failed or abandoned import costs nothing.
     *
     * @param eventId the event that was imported into
     */
    void recordFullImport(Long eventId);

    /**
     * Records that an add-on import finished successfully, spending one of the event's add-on
     * allowance.
     *
     * @param eventId the event that was imported into
     */
    void recordAddOnImport(Long eventId);
}
