package com.timekeeper.bibexpo.model.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Category of an in-app notification — used by the frontend for the icon and the deep-link route")
public enum NotificationType {
    IMPORT_COMPLETED,
    IMPORT_FAILED,
    SHORT_URLS_COMPLETED,
    EVENT_CREATED,
    EVENT_PUBLISHED,
    EVENT_CANCELLED,
    EVENT_COMPLETED,
    CAMPAIGN_COMPLETED,
    CAMPAIGN_FAILED
}
