package com.timekeeper.bibexpo.event.api;

/**
 * How many participants an event's category still holds, so the event module can refuse to delete
 * a category that is in use. Declared here and implemented by participant, which owns the roster —
 * the same direction as {@link EventCampaignUsage}.
 */
public interface CategoryUsage {

    /**
     * @param eventId    the event the category belongs to
     * @param categoryId the category
     * @return how many of the event's participants are assigned to that category
     */
    long countParticipants(Long eventId, Long categoryId);
}
