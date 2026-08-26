package com.timekeeper.bibexpo.event.api;

/**
 * The current race and category names of an event, for the modules that store only ids.
 *
 * <p>Participants, distribution logs and campaign recipients all carry {@code raceId} and
 * {@code categoryId} and have to show names, so seven modules read this on nearly every scan. The
 * lookup maps are cached per event and rebuilt on the next read after any race or category write,
 * which is the event module's own business; this interface is the whole of what the readers get.
 */
public interface RaceCategoryNameQuery {

    /**
     * Returns the race and category names currently in force for an event.
     *
     * @param eventId the event
     * @return the name maps, empty when the event has no races or categories
     */
    EventNames forEvent(Long eventId);
}
