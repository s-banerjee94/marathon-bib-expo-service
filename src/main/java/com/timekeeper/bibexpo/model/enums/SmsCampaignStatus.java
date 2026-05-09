package com.timekeeper.bibexpo.model.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Campaign lifecycle status: DRAFT = created, not yet armed; ACTIVE = armed and running; SENT = completed (SCHEDULED/MANUAL only)")
public enum SmsCampaignStatus {
    DRAFT,
    ACTIVE,
    SENT
}
