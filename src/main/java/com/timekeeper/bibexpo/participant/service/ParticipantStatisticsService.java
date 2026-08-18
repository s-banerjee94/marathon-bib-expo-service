package com.timekeeper.bibexpo.participant.service;

import com.timekeeper.bibexpo.participant.model.dto.response.ParticipantStatisticsResponse;
import com.timekeeper.bibexpo.user.model.entity.User;

/**
 * Read-side aggregation of an event's participant statistics from the
 * pre-maintained stats counters.
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
}
