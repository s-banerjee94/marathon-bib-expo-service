package com.timekeeper.bibexpo.event.api;

import com.timekeeper.bibexpo.event.stats.model.dynamodb.EventStatsDDB;

import java.util.List;

/**
 * The read side of the counter rows {@link EventStatsRecorder} maintains.
 *
 * <p>Three kinds of caller use it: quota checks and the distribution counter, which want a single
 * total without paging the roster, and the two aggregation surfaces — participant statistics and
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
     * How many participants have not collected their bib yet, as the counters have it: TOTAL less
     * BIB_COLLECTED, never below zero. Two single-row reads, however large the roster.
     *
     * @param eventId the event to count
     * @return the participants still to collect their bib
     */
    long pendingBibCount(Long eventId);

    /**
     * How many participants have collected their bib but are still owed a goody of their own, as the
     * counters have it; never below zero. One single-row read. An event whose counters predate this one
     * reads zero until its statistics are reconciled.
     *
     * @param eventId the event to count
     * @return the participants still to collect a goody
     */
    long pendingGoodiesCount(Long eventId);

    /**
     * Reads every counter row of an event.
     *
     * @param eventId the event whose counters to read
     * @return the rows, empty when the event has never had a counter written
     */
    List<EventStatsDDB> counters(Long eventId);

    /**
     * What the event's roster was promised, one row per goody per distinct spelling, with how many of
     * those participants were already handed the goody.
     *
     * <p>Decoded here rather than left as raw rows, so a caller outside this module never has to
     * learn how an entitlement key is spelled. Reads the same single query as {@link #counters},
     * needs no index, and is empty for an event imported before these counters existed until its
     * statistics are reconciled. Likewise, the handed-out count of an event whose counters were last
     * rebuilt before hand-outs were counted by value is unreliable until it is reconciled.
     *
     * @param eventId the event to read
     * @return the entitlements, in goody then value order
     */
    List<GoodieEntitlement> entitlements(Long eventId);
}
