package com.timekeeper.bibexpo.participant.service.util;

import com.timekeeper.bibexpo.event.api.ParticipantCounters;
import com.timekeeper.bibexpo.participant.model.dynamodb.ParticipantDDB;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reduces a participant row to the dimensions the event-stats counters are keyed by.
 *
 * <p>It also settles the pre-mutation snapshots the update and undo paths need: the result is
 * immutable, so taking it before the write is all the snapshotting those paths have to do.
 */
public final class ParticipantCountersMapper {

    private ParticipantCountersMapper() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static ParticipantCounters of(ParticipantDDB participant) {
        Map<String, String> goodies = participant.getGoodiesDistribution();
        Map<String, String> entitled = participant.getGoodies();
        return new ParticipantCounters(
                participant.getEventId(),
                participant.getRaceId(),
                participant.getCategoryId(),
                participant.getGender(),
                participant.getBibCollectedAt(),
                DistributorStamp.userIdOf(participant.getBibDistributedBy()),
                goodies == null ? Set.of() : goodies.keySet(),
                entitled);
    }

    public static List<ParticipantCounters> of(List<ParticipantDDB> participants) {
        return participants.stream().map(ParticipantCountersMapper::of).toList();
    }
}
