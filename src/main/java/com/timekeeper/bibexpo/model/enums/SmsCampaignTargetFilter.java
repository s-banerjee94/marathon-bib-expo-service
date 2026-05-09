package com.timekeeper.bibexpo.model.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Who receives the SMS: ALL = every participant in the event; NOT_COLLECTED = only participants who have not yet collected their bib")
public enum SmsCampaignTargetFilter {
    ALL,
    NOT_COLLECTED
}
