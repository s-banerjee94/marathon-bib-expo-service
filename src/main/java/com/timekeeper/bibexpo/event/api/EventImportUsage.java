package com.timekeeper.bibexpo.event.api;

/**
 * How many imports an event has already used, so the event module can refuse to set an import
 * limit below what has been run. Declared here and implemented by the importer, which owns the
 * import job history — the same direction as {@link EventBillingGuard}.
 */
public interface EventImportUsage {

    /**
     * @param eventId the event
     * @return how many full imports have been run for the event
     */
    long countFullImports(Long eventId);

    /**
     * @param eventId the event
     * @return how many add-on imports have been run for the event
     */
    long countAddOnImports(Long eventId);
}
