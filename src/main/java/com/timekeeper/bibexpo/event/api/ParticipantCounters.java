package com.timekeeper.bibexpo.event.api;

import java.util.Set;

/**
 * The dimensions of one participant that the event-stats counters are keyed by.
 *
 * <p>Stats counts participants but must not depend on the participant module, so
 * {@link EventStatsRecorder} takes this record rather than a participant row. Being a record also
 * makes it a snapshot: the update and undo paths have to report the state a row was in before they
 * mutated it, and an immutable copy is exactly what they need.
 *
 * @param eventId          the owning event, spelled as the stats partition key spells it
 * @param raceId           the participant's race, or null
 * @param categoryId       the participant's category, or null
 * @param gender           gender as stored; anything that is not M or F counts as other
 * @param bibCollectedAt   ISO-8601 instant the bib was collected, or null when it was not
 * @param bibDistributorId id of the staff member who handed the bib over, or null
 * @param goodiesCollected names of the goodies this participant has taken delivery of
 */
public record ParticipantCounters(
        String eventId,
        String raceId,
        String categoryId,
        String gender,
        String bibCollectedAt,
        String bibDistributorId,
        Set<String> goodiesCollected) {

    public ParticipantCounters {
        goodiesCollected = goodiesCollected == null ? Set.of() : Set.copyOf(goodiesCollected);
    }

    public boolean bibCollected() {
        return bibCollectedAt != null && !bibCollectedAt.isBlank();
    }
}
