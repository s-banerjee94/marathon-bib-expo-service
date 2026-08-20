package com.timekeeper.bibexpo.event.api;

/**
 * How many campaigns and templates an event already holds, so the event module can refuse to set
 * a limit below what exists. Declared here and implemented by messaging, which owns them — the
 * same direction as {@link EventBillingGuard}.
 */
public interface EventCampaignUsage {

    /**
     * @param eventId the event
     * @return how many SMS templates the event holds
     */
    long countSmsTemplates(Long eventId);

    /**
     * @param eventId the event
     * @return how many SMS campaigns the event holds
     */
    long countSmsCampaigns(Long eventId);
}
