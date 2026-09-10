package com.timekeeper.bibexpo.participant.service;

import com.timekeeper.bibexpo.event.model.entity.Event;
import com.timekeeper.bibexpo.participant.model.dto.response.ParticipantStatisticsResponse;
import com.timekeeper.bibexpo.user.model.entity.User;

/**
 * Aggregation of an event's participant statistics from the pre-maintained stats counters, and
 * the rebuild of those counters from the roster they count.
 */
public interface ParticipantStatisticsService {

    /**
     * Get aggregated statistics for participants in an event.
     * Includes total count, bib collection status, breakdown by race, category, and gender.
     * @param eventId The event ID
     * @param currentUser The authenticated user
     * @return Participant statistics
     */
    ParticipantStatisticsResponse getParticipantStatistics(Long eventId, User currentUser);

    /**
     * Rebuild the counters this service reads from the participant rows themselves.
     * Used after a batch import, which writes participants without going through the per-write
     * counters, and as the recovery path when the counters have drifted.
     * @param eventId The event whose counters to rebuild
     * @param currentUser The authenticated user
     */
    void reconcile(Long eventId, User currentUser);

    /**
     * Rebuild the counters from the participant rows, for a caller that has already authorised the
     * change that made them stale, such as removing an imported goody from every row that carries it.
     * @param event The event whose counters to rebuild
     */
    void rebuild(Event event);
}
