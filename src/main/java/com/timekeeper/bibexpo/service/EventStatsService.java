package com.timekeeper.bibexpo.service;

import com.timekeeper.bibexpo.model.dto.response.ParticipantStatisticsResponse;
import com.timekeeper.bibexpo.model.dynamodb.ParticipantDDB;
import com.timekeeper.bibexpo.model.entity.User;

import java.util.List;

/**
 * Maintains pre-aggregated counter rows in the marathon-event-stats DynamoDB table
 * to serve the per-event statistics endpoint without scanning participants on every read.
 * <p>
 * All on* methods are best-effort: counter failures are logged but never propagated to
 * the caller, so a failed counter update does not break the user-facing operation.
 * Drift is healed by the reconciler.
 */
public interface EventStatsService {

    /**
     * Increment counters for a newly created participant.
     * Updates TOTAL, RACE#&lt;id&gt;, CATEGORY#&lt;id&gt;, GENDER#&lt;g&gt;.
     * @param participant The newly created participant
     */
    void onParticipantCreated(ParticipantDDB participant);

    /**
     * Decrement counters for a deleted participant.
     * Mirrors the dimensions decremented by onParticipantCreated, plus the collected
     * dimensions and goodies counters if the participant had collected their bib.
     * @param participant The participant that was deleted (must be the row as it existed pre-delete)
     */
    void onParticipantDeleted(ParticipantDDB participant);

    /**
     * Apply counter deltas for a participant whose raceId, categoryId, or gender changed.
     * Decrements the old dimension keys and increments the new ones. No-op if none of these
     * three fields changed.
     * @param before The participant row before the update
     * @param after  The participant row after the update
     */
    void onParticipantUpdated(ParticipantDDB before, ParticipantDDB after);

    /**
     * Increment bib-collected counters for a participant whose bib was just collected.
     * Updates BIB_COLLECTED, RACE#&lt;id&gt;#COLLECTED, CATEGORY#&lt;id&gt;#COLLECTED, plus a
     * GOODIE#&lt;name&gt;#DISTRIBUTED counter for each goodie distributed in the same operation.
     * @param participant         The participant after the bib-collect write
     * @param goodiesDistributed  Names of goodies distributed alongside the bib (may be empty)
     */
    void onBibCollected(ParticipantDDB participant, List<String> goodiesDistributed);

    /**
     * Decrement bib-collected counters for a participant whose bib collection was undone.
     * The participant snapshot must be captured BEFORE the undo mutation, since undoBib
     * clears bibCollectedAt and goodiesDistribution from the row.
     * @param participantBefore The participant snapshot taken before the undo write
     */
    void onBibUndone(ParticipantDDB participantBefore);

    /**
     * Increment goodie-distribution counters when goodies are distributed without a
     * concurrent bib collection.
     * @param participant The participant after the distribute-goodies write
     * @param items       Names of goodies distributed in this operation
     */
    void onGoodiesDistributed(ParticipantDDB participant, List<String> items);

    /**
     * Decrement counters for a batch of participants deleted via bulk delete.
     * @param participants The participants that were deleted (must be the rows as they existed pre-delete)
     */
    void onBulkDeleted(List<ParticipantDDB> participants);

    /**
     * Rebuild the entire counter table for an event from the source-of-truth participant rows.
     * Wipes existing counter rows for the event, queries all participants, aggregates in memory,
     * and writes fresh counter rows. Used for initial backfill, after batch import, and as a drift
     * recovery tool.
     * @param eventId     The event whose counters to rebuild
     * @param currentUser The authenticated user (used for authorization and event-enabled checks)
     * @return The freshly computed statistics response
     */
    ParticipantStatisticsResponse reconcile(Long eventId, User currentUser);
}
