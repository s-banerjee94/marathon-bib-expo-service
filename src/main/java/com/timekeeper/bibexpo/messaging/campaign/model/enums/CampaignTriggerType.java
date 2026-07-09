package com.timekeeper.bibexpo.messaging.campaign.model.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "How the campaign is triggered: AUTO_BIB_COLLECTED fires per participant on bib collection (stays ACTIVE indefinitely, one per event); SCHEDULED fires once at scheduledAt")
public enum CampaignTriggerType {
    AUTO_BIB_COLLECTED,
    SCHEDULED
}
