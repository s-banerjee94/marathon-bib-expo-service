package com.timekeeper.bibexpo.messaging.campaign.model.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Who receives the message: ALL = every participant in the event; NOT_COLLECTED = only participants who have not yet collected their bib")
public enum CampaignTargetFilter {
    ALL,
    NOT_COLLECTED
}
