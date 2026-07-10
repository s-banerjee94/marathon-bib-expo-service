package com.timekeeper.bibexpo.messaging.campaign.model.dto.request;

import com.timekeeper.bibexpo.messaging.campaign.model.enums.CampaignTargetFilter;
import com.timekeeper.bibexpo.messaging.campaign.model.enums.CampaignTriggerType;

/**
 * Common accessors of the campaign create/update request payloads across channels. The SMS and
 * WhatsApp DTOs implement it through their Lombok-generated getters so the shared campaign
 * service base class can read the arm parameters; the wire format of each DTO is unchanged.
 */
public interface CampaignWriteRequest {

    /**
     * Campaign name; required on create, optional on update.
     */
    String getName();

    /**
     * When present the campaign is armed and moves to ACTIVE.
     */
    CampaignTriggerType getTriggerType();

    /**
     * Required whenever {@link #getTriggerType()} is present.
     */
    CampaignTargetFilter getTargetFilter();

    /**
     * Scheduled send date (yyyy-MM-dd) for SCHEDULED campaigns.
     */
    String getScheduledDate();

    /**
     * Scheduled send time (HH:mm) for SCHEDULED campaigns.
     */
    String getScheduledTime();
}
