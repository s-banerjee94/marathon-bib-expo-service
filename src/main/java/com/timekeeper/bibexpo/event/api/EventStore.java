package com.timekeeper.bibexpo.event.api;

import com.timekeeper.bibexpo.event.model.entity.Event;

import java.util.Optional;

/**
 * The event record as the rest of the application may use it.
 *
 * <p>Nearly every module needs an event: billing bills one, the importer imports into one,
 * messaging sends on behalf of one, and each reached {@code EventRepository} to fetch it. That
 * traffic is fine by direction; what it must not do is hold the repository, so this interface is
 * the whole of the surface they get. Anything not declared here is the event module's own business.
 *
 * <p>The two writes are the only event fields an outer module legitimately changes, and both are
 * one-way flags rather than edits — the full event write path stays behind {@code EventService}.
 */
public interface EventStore {

    /**
     * Returns the event with the given id.
     *
     * @param eventId the event id
     * @return the event, never {@code null}
     * @throws com.timekeeper.bibexpo.event.exception.EventNotFoundException if no event has that id
     */
    Event requireById(Long eventId);

    /**
     * Returns the event with the given id, for callers that treat a missing event as simply
     * having nothing to do rather than as an error.
     *
     * @param eventId the event id
     * @return the event, or empty when no event has that id
     */
    Optional<Event> findById(Long eventId);

    /**
     * Counts an organization's events, for callers guarding against deleting an organization
     * that still owns some.
     *
     * @param organizationId the owning organization
     * @return how many events the organization has
     */
    long countByOrganizationId(Long organizationId);

    /**
     * Marks that distribution has begun for the event, which closes it to the changes that are
     * only safe before the first bib goes out. Does nothing when the flag is already set.
     *
     * @param eventId the event whose distribution has started
     */
    void markDistributionStarted(Long eventId);

    /**
     * Replaces the event's configured goodies list, for the importer, which learns the goodies
     * columns only once the CSV has been read.
     *
     * @param eventId     the event to update
     * @param goodiesJson the goodies list as the JSON array the event stores
     */
    void updateGoodies(Long eventId, String goodiesJson);
}
