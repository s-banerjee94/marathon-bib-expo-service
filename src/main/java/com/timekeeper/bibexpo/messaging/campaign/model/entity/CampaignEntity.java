package com.timekeeper.bibexpo.messaging.campaign.model.entity;

import com.timekeeper.bibexpo.messaging.campaign.model.enums.CampaignStatus;
import com.timekeeper.bibexpo.messaging.campaign.model.enums.CampaignTargetFilter;
import com.timekeeper.bibexpo.messaging.campaign.model.enums.CampaignTriggerType;

import java.time.Instant;

/**
 * Channel-agnostic view of a campaign entity. {@link SmsCampaign} and {@link WhatsAppCampaign}
 * satisfy it through their Lombok-generated accessors, letting the shared campaign service
 * base class run the arm/disarm/lifecycle logic without knowing the channel.
 */
public interface CampaignEntity {

    /**
     * Database identifier of the campaign.
     */
    Long getId();

    /**
     * Human-readable campaign name.
     */
    String getName();

    void setName(String name);

    /**
     * Lifecycle status (DRAFT, ACTIVE, SENDING, SENT, ...).
     */
    CampaignStatus getStatus();

    void setStatus(CampaignStatus status);

    /**
     * How the campaign fires once armed; null while DRAFT.
     */
    CampaignTriggerType getTriggerType();

    void setTriggerType(CampaignTriggerType triggerType);

    /**
     * Which participants the campaign targets; null while DRAFT.
     */
    CampaignTargetFilter getTargetFilter();

    void setTargetFilter(CampaignTargetFilter targetFilter);

    /**
     * Send instant for SCHEDULED campaigns; null otherwise.
     */
    Instant getScheduledAt();

    void setScheduledAt(Instant scheduledAt);
}
