package com.timekeeper.bibexpo.messaging.campaign.util;

import com.timekeeper.bibexpo.model.dto.notification.NotifyRequest;
import com.timekeeper.bibexpo.model.enums.NotificationAudience;
import com.timekeeper.bibexpo.model.enums.NotificationType;
import com.timekeeper.bibexpo.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Emits the in-app notifications for campaign lifecycle events, shared by both SMS and WhatsApp.
 * Completion is reported to the organization's staff; failure additionally reaches the platform
 * owners (ROOT/ADMIN), since a campaign often fails because of a shared gateway only they can fix.
 */
@Component
@RequiredArgsConstructor
public class CampaignNotifier {

    private static final String ENTITY_TYPE = "CAMPAIGN";

    private final NotificationService notificationService;

    public void notifyCompleted(Long campaignId, String campaignName, Long organizationId, String channel, int sentCount) {
        notificationService.notify(NotifyRequest.builder()
                .audience(NotificationAudience.ORGANIZATION_STAFF)
                .organizationId(organizationId)
                .type(NotificationType.CAMPAIGN_COMPLETED)
                .title("Campaign Sent")
                .message(String.format("Your %s campaign \"%s\" finished — %d message(s) sent.",
                        channel, campaignName, sentCount))
                .entityType(ENTITY_TYPE)
                .entityId(String.valueOf(campaignId))
                .build());
    }

    public void notifyFailed(Long campaignId, String campaignName, Long organizationId, String channel) {
        String title = "Campaign Failed";
        String message = String.format("The %s campaign \"%s\" failed to send.", channel, campaignName);

        notificationService.notify(NotifyRequest.builder()
                .audience(NotificationAudience.ORGANIZATION_STAFF)
                .organizationId(organizationId)
                .type(NotificationType.CAMPAIGN_FAILED)
                .title(title)
                .message(message)
                .entityType(ENTITY_TYPE)
                .entityId(String.valueOf(campaignId))
                .build());

        notificationService.notify(NotifyRequest.builder()
                .audience(NotificationAudience.PLATFORM_ADMINS)
                .type(NotificationType.CAMPAIGN_FAILED)
                .title(title)
                .message(message)
                .entityType(ENTITY_TYPE)
                .entityId(String.valueOf(campaignId))
                .build());
    }
}
