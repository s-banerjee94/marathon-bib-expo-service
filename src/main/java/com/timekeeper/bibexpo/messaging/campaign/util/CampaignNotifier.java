package com.timekeeper.bibexpo.messaging.campaign.util;

import com.timekeeper.bibexpo.notification.model.dto.NotifyRequest;
import com.timekeeper.bibexpo.notification.model.enums.NotificationAudience;
import com.timekeeper.bibexpo.notification.model.enums.NotificationType;
import com.timekeeper.bibexpo.notification.service.NotificationService;
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
        notifyFailed(campaignId, campaignName, organizationId, channel, null);
    }

    /**
     * Reports a failed campaign, naming the reason when there is an actionable one — a template the
     * organization's sender cannot fill, for instance, which nobody can fix without being told what
     * is wrong.
     *
     * @param reason operator-facing explanation, or null for an unqualified failure
     */
    public void notifyFailed(Long campaignId, String campaignName, Long organizationId, String channel, String reason) {
        String title = "Campaign Failed";
        String message = reason == null
                ? String.format("The %s campaign \"%s\" failed to send.", channel, campaignName)
                : String.format("The %s campaign \"%s\" was not sent. %s", channel, campaignName, reason);

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
