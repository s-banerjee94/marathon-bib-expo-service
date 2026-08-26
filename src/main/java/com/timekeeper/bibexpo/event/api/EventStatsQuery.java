package com.timekeeper.bibexpo.event.api;

import com.timekeeper.bibexpo.event.stats.model.dynamodb.EventStatsDDB;

import java.util.List;

/**
 * The read side of the counter rows {@link EventStatsRecorder} maintains.
 *
 * <p>Two kinds of caller use it: quota checks, which want nothing but the participant total and
 * want it without paging the roster, and the two aggregation surfaces — participant statistics and
 * the dashboard activity block — which parse the whole key space themselves. The rows come back as
 * the stored records because both readers key off the statKey vocabulary those records carry.
 */
public interface EventStatsQuery {

    /**
     * Reads the TOTAL counter, the participant count as the counters have it.
     *
     * @param eventId the event to count
     * @return the counted participants, or 0 when the event has no TOTAL row
     */
    long participantCount(Long eventId);

    /**
     * Reads every counter row of an event.
     *
     * @param eventId the event whose counters to read
     * @return the rows, empty when the event has never had a counter written
     */
    List<EventStatsDDB> counters(Long eventId);
}
