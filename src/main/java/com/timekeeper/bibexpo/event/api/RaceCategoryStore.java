package com.timekeeper.bibexpo.event.api;

import com.timekeeper.bibexpo.event.model.entity.Event;
import com.timekeeper.bibexpo.event.race.category.model.entity.Category;
import com.timekeeper.bibexpo.event.race.model.entity.Race;

import java.util.Set;

/**
 * The races and categories of an event as the importer may use them.
 *
 * <p>A CSV names its races and categories in free text and creates whichever ones the event does
 * not have yet, so the import pipeline both reads and writes them. It used to hold
 * {@code RaceRepository} and {@code CategoryRepository} to do it; this interface is the whole of
 * the surface it gets instead. Names arrive raw and are normalised here, so a caller never has to
 * know the stored form.
 *
 * <p>Creating through this port also drops the cached names for the event, which is why the
 * importer no longer evicts anything itself.
 */
public interface RaceCategoryStore {

    /**
     * Returns the event's race of that name, creating it when the event has none.
     *
     * @param rawName the race name as it appeared in the CSV
     * @param event   the owning event, for the new race's association
     * @return the existing or newly created race
     */
    Race findOrCreateRace(String rawName, Event event);

    /**
     * Returns the race's category of that name, creating it when the race has none.
     *
     * @param rawName the category name as it appeared in the CSV
     * @param eventId the owning event, which the race's own association cannot be relied on for
     *                once the race has left the transaction that loaded it
     * @param race    the owning race
     * @return the existing or newly created category
     */
    Category findOrCreateCategory(String rawName, Long eventId, Race race);

    /**
     * @param eventId the event
     * @return how many races the event holds
     */
    int countRaces(Long eventId);

    /**
     * Resolves a race name to its id without creating anything, for callers checking a whole CSV
     * against the event's limits before any of it is written.
     *
     * @param eventId the owning event
     * @param rawName the race name as it appeared in the CSV
     * @return the race id, or {@code null} when the event has no race of that name
     */
    Long findRaceId(Long eventId, String rawName);

    /**
     * @param raceId the race
     * @return the stored names of the race's categories
     */
    Set<String> categoryNames(Long raceId);
}
