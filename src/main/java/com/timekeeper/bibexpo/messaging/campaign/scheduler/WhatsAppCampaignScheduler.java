package com.timekeeper.bibexpo.messaging.campaign.scheduler;

import com.timekeeper.bibexpo.messaging.campaign.model.enums.CampaignStatus;
import com.timekeeper.bibexpo.messaging.campaign.model.enums.CampaignTriggerType;
import com.timekeeper.bibexpo.messaging.campaign.config.WhatsAppSchedulerProperties;
import com.timekeeper.bibexpo.messaging.campaign.model.entity.WhatsAppCampaign;
import com.timekeeper.bibexpo.messaging.campaign.repository.WhatsAppCampaignRepository;
import com.timekeeper.bibexpo.messaging.campaign.service.WhatsAppCampaignSendService;
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

    @Scheduled(fixedDelay = 60_000)
    public void tick() {
        Instant now = Instant.now();

        fireDueCampaigns(now);
        recoverStuckCampaigns(now);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverOnStartup() {
        List<WhatsAppCampaign> sending = campaignRepository.findAllByStatus(CampaignStatus.SENDING);
        if (sending.isEmpty()) {
            return;
        }
        log.info("Recovering {} SENDING WhatsApp campaign(s) found after startup", sending.size());
        for (WhatsAppCampaign campaign : sending) {
            log.info("Recovering SENDING WhatsApp campaign ID: {} after restart", campaign.getId());
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
            log.info("Fired scheduled WhatsApp campaign ID: {} for event ID: {}", campaign.getId(), campaign.getEventId());
        }
    }

    private void recoverStuckCampaigns(Instant now) {
        Instant stuckThreshold = now.minusSeconds(schedulerProperties.getStuckThresholdMinutes() * 60L);
        List<WhatsAppCampaign> stuck = campaignRepository.findStuckCampaigns(CampaignStatus.SENDING, stuckThreshold);

        for (WhatsAppCampaign campaign : stuck) {
            if (campaign.getRetryCount() > schedulerProperties.getMaxRetryCount()) {
                campaign.setStatus(CampaignStatus.FAILED);
                campaignRepository.save(campaign);
                log.error("WhatsApp campaign ID: {} exceeded max retries ({}) — marked FAILED",
                        campaign.getId(), schedulerProperties.getMaxRetryCount());
            } else {
                campaign.setRetryCount(campaign.getRetryCount() + 1);
                campaignRepository.save(campaign);
                campaignSendService.sendCampaignAsync(campaign.getId());
                log.warn("Retrying stuck WhatsApp campaign ID: {} (attempt {})", campaign.getId(), campaign.getRetryCount());
            }
        }
    }
}
