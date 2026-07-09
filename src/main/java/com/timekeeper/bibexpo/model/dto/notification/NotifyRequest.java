package com.timekeeper.bibexpo.model.dto.notification;

import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.enums.NotificationAudience;
import com.timekeeper.bibexpo.model.enums.NotificationType;
import lombok.Builder;
import lombok.Data;

/**
 * Internal command describing one notification to fan out. Built by whichever backend operation
 * wants to notify someone; never exposed over the API.
 */
@Data
@Builder
public class NotifyRequest {

    /** Who receives it. */
    private NotificationAudience audience;

    /** Required for {@code ORGANIZATION_*} audiences. */
    private Long organizationId;

    /** Required for the {@code USER} audience. */
    private Long targetUserId;

    private NotificationType type;
    private String title;
    private String message;

    /** Optional deep-link target, e.g. {@code EVENT} / {@code CAMPAIGN}. */
    private String entityType;
    private String entityId;

    /** Whoever triggered the action; excluded from the recipient list and recorded as {@code actorName}. */
    private User actor;
}
