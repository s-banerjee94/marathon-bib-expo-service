package com.timekeeper.bibexpo.event.api;

import java.time.ZoneId;
import java.util.List;
import java.util.stream.Stream;

/**
 * Maintains pre-aggregated counter rows in the marathon-event-stats DynamoDB table so the event
 * dashboard rollup and the participant statistics can be served without scanning participants on
 * every read.
 * <p>
 * All on* methods are best-effort: counter failures are logged but never propagated to
 * the caller, so a failed counter update does not break the user-facing operation.
 * Drift is healed by {@link #rebuild}.
 */
public interface EventStatsRecorder {

    /**
     * Increment counters for a newly created participant.
     * Updates TOTAL, RACE#&lt;id&gt;, CATEGORY#&lt;id&gt;, GENDER#&lt;g&gt;.
     * @param participant The newly created participant
     */
    void onParticipantCreated(ParticipantCounters participant);

    /**
     * Decrement counters for a deleted participant.
     * Mirrors the dimensions decremented by onParticipantCreated, plus the collected
     * dimensions and goodies counters if the participant had collected their bib.
     * @param participant The participant that was deleted, as it existed pre-delete
     */
    void onParticipantDeleted(ParticipantCounters participant);

    /**
     * Apply counter deltas for a participant whose raceId, categoryId, or gender changed.
     * Decrements the old dimension keys and increments the new ones. No-op if none of these
     * three fields changed.
     * @param before The participant before the update
     * @param after  The participant after the update
     */
    void onParticipantUpdated(ParticipantCounters before, ParticipantCounters after);

    /**
     * Increment bib-collected counters for a participant whose bib was just collected.
     * Updates BIB_COLLECTED, RACE#&lt;id&gt;#COLLECTED, CATEGORY#&lt;id&gt;#COLLECTED, plus a
     * GOODIE#&lt;name&gt;#DISTRIBUTED counter for each goodie distributed in the same operation, and
     * GOODIES_PENDING when the participant is still owed a goody of their own.
     * Also bumps the range-scoped activity counters HOUR#&lt;date&gt;#&lt;hh&gt; and
     * DIST#&lt;date&gt;#&lt;distributorId&gt;, bucketed in the event's time zone.
     * @param participant         The participant after the bib-collect write
     * @param goodiesDistributed  Names of goodies distributed alongside the bib (may be empty)
     * @param eventZone           The event's time zone, used to bucket the collection by local date and hour
     */
    void onBibCollected(ParticipantCounters participant, List<String> goodiesDistributed, ZoneId eventZone);

    /**
     * Decrement bib-collected counters for a participant whose bib collection was undone.
     * The snapshot must be taken BEFORE the undo mutation, since undoBib clears bibCollectedAt
     * and the goodies from the row. Reverses the range-scoped activity counters using the
     * snapshot's original collection time and distributor.
     * @param participantBefore The participant as it was before the undo write
     * @param eventZone         The event's time zone, used to locate the original activity bucket
     */
    void onBibUndone(ParticipantCounters participantBefore, ZoneId eventZone);

    /**
     * Increment goodie-distribution counters when goodies are distributed without a
     * concurrent bib collection, and take the participant off GOODIES_PENDING once they are owed
     * nothing more.
     * @param participant The participant after the distribute-goodies write
     * @param items       Names of goodies distributed in this operation
     */
    void onGoodiesDistributed(ParticipantCounters participant, List<String> items);

    /**
     * Decrement counters for a batch of participants deleted via bulk delete.
     * @param participants The participants that were deleted, as they existed pre-delete
     */
    void onBulkDeleted(List<ParticipantCounters> participants);

    /**
     * Drop every counter row for an event, for the caller that has just emptied its roster.
     * @param eventId The event whose counters are now meaningless
     */
    void onAllDeleted(Long eventId);

    /**
     * Rebuild the entire counter table for an event from the source-of-truth roster.
     * Wipes the existing counter rows, aggregates the supplied participants in memory, and writes
     * fresh rows. Used after batch import and as a drift recovery tool. Unlike the on* methods this
     * one reports failure to its caller.
     * @param eventId   The event whose counters to rebuild
     * @param eventZone The event's time zone, used to bucket collections by local date and hour
     * @param roster    Every participant of the event, consumed once
     * @return what was walked and written
     */
    EventStatsRebuild rebuild(Long eventId, ZoneId eventZone, Stream<ParticipantCounters> roster);
}
