package com.timekeeper.bibexpo.model.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Campaign lifecycle status: DRAFT = created, not yet armed; ACTIVE = armed and will fire at scheduledAt; SENDING = batch dispatch in progress; SENT = completed; FAILED = dispatch failed after max retries")
public enum CampaignStatus {
    DRAFT,
    ACTIVE,
    SENDING,
    SENT,
    FAILED
}
