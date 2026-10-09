package com.timekeeper.bibexpo.messaging.campaign.scheduler;

import com.timekeeper.bibexpo.messaging.campaign.model.enums.CampaignStatus;
import com.timekeeper.bibexpo.messaging.campaign.model.enums.CampaignTriggerType;
import com.timekeeper.bibexpo.messaging.campaign.config.WhatsAppSchedulerProperties;
import com.timekeeper.bibexpo.messaging.campaign.model.entity.WhatsAppCampaign;
import com.timekeeper.bibexpo.messaging.campaign.repository.WhatsAppCampaignRepository;
import com.timekeeper.bibexpo.messaging.campaign.service.WhatsAppCampaignSendService;
import com.timekeeper.bibexpo.messaging.campaign.util.CampaignNotifier;
import com.timekeeper.bibexpo.messaging.shared.enums.MessageChannel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class WhatsAppCampaignScheduler {

    private final WhatsAppCampaignRepository campaignRepository;
    private final WhatsAppCampaignSendService campaignSendService;
    private final WhatsAppSchedulerProperties schedulerProperties;
    private final CampaignNotifier campaignNotifier;

    @Scheduled(fixedDelay = 60_000)
    public void tick() {
        Instant now = Instant.now();

        fireDueCampaigns(now);
        recoverStuckCampaigns(now);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverOnStartup() {
        for (WhatsAppCampaign campaign : campaignRepository.findAllByStatus(CampaignStatus.SENDING)) {
            log.atInfo()
                    .addKeyValue("event", "campaign.resumed")
                    .addKeyValue("channel", MessageChannel.WHATSAPP)
                    .addKeyValue("eventId", campaign.getEventId())
                    .addKeyValue("campaignId", campaign.getId())
                    .log("Campaign resumed after startup");
            campaignSendService.sendCampaignAsync(campaign.getId());
        }
    }

    private void fireDueCampaigns(Instant now) {
        List<WhatsAppCampaign> due = campaignRepository.findDueCampaigns(
                CampaignTriggerType.SCHEDULED, CampaignStatus.ACTIVE, now);

        for (WhatsAppCampaign campaign : due) {
            campaign.setStatus(CampaignStatus.SENDING);
            campaignRepository.save(campaign);
            campaignSendService.sendCampaignAsync(campaign.getId());
            log.atInfo()
                    .addKeyValue("event", "campaign.fired")
                    .addKeyValue("channel", MessageChannel.WHATSAPP)
                    .addKeyValue("eventId", campaign.getEventId())
                    .addKeyValue("campaignId", campaign.getId())
                    .log("Scheduled campaign fired");
        }
    }

    private void recoverStuckCampaigns(Instant now) {
        Instant stuckThreshold = now.minusSeconds(schedulerProperties.getStuckThresholdMinutes() * 60L);
        List<WhatsAppCampaign> stuck = campaignRepository.findStuckCampaigns(CampaignStatus.SENDING, stuckThreshold);

        for (WhatsAppCampaign campaign : stuck) {
            if (campaign.getRetryCount() >= schedulerProperties.getMaxRetryCount()) {
                campaign.setStatus(CampaignStatus.FAILED);
                campaignRepository.save(campaign);
                log.atError()
                        .addKeyValue("event", "campaign.failed")
                        .addKeyValue("channel", MessageChannel.WHATSAPP)
                        .addKeyValue("eventId", campaign.getEventId())
                        .addKeyValue("campaignId", campaign.getId())
                        .addKeyValue("reason", "retries_exhausted")
                        .addKeyValue("retryCount", campaign.getRetryCount())
                        .log("Campaign failed");
                campaignNotifier.notifyFailed(campaign.getId(), campaign.getName(), campaign.getOrganizationId(), "WhatsApp");
            } else {
                campaign.setRetryCount(campaign.getRetryCount() + 1);
                campaignRepository.save(campaign);
                campaignSendService.sendCampaignAsync(campaign.getId());
                log.atWarn()
                        .addKeyValue("event", "campaign.retried")
                        .addKeyValue("channel", MessageChannel.WHATSAPP)
                        .addKeyValue("eventId", campaign.getEventId())
                        .addKeyValue("campaignId", campaign.getId())
                        .addKeyValue("retryCount", campaign.getRetryCount())
                        .log("Stuck campaign retried");
            }
        }
    }
}
