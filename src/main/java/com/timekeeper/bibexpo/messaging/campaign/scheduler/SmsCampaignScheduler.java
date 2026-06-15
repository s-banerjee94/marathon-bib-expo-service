package com.timekeeper.bibexpo.messaging.campaign.scheduler;

import com.timekeeper.bibexpo.messaging.campaign.config.SmsSchedulerProperties;
import com.timekeeper.bibexpo.messaging.campaign.model.entity.SmsCampaign;
import com.timekeeper.bibexpo.messaging.campaign.model.enums.CampaignStatus;
import com.timekeeper.bibexpo.messaging.campaign.model.enums.CampaignTriggerType;
import com.timekeeper.bibexpo.messaging.campaign.repository.SmsCampaignRepository;
import com.timekeeper.bibexpo.messaging.campaign.service.SmsCampaignSendService;
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
public class SmsCampaignScheduler {

    private final SmsCampaignRepository smsCampaignRepository;
    private final SmsCampaignSendService smsCampaignSendService;
    private final SmsSchedulerProperties schedulerProperties;

    @Scheduled(fixedDelay = 60_000)
    public void tick() {
        Instant now = Instant.now();

        fireDueCampaigns(now);
        recoverStuckCampaigns(now);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverOnStartup() {
        List<SmsCampaign> sending = smsCampaignRepository.findAllByStatus(CampaignStatus.SENDING);
        if (sending.isEmpty()) {
            return;
        }
        log.info("Recovering {} SENDING campaign(s) found after startup", sending.size());
        for (SmsCampaign campaign : sending) {
            log.info("Recovering SENDING campaign ID: {} after restart", campaign.getId());
            smsCampaignSendService.sendCampaignAsync(campaign.getId());
        }
    }

    private void fireDueCampaigns(Instant now) {
        List<SmsCampaign> due = smsCampaignRepository.findDueCampaigns(
                CampaignTriggerType.SCHEDULED, CampaignStatus.ACTIVE, now);

        for (SmsCampaign campaign : due) {
            campaign.setStatus(CampaignStatus.SENDING);
            smsCampaignRepository.save(campaign);
            smsCampaignSendService.sendCampaignAsync(campaign.getId());
            log.info("Fired scheduled campaign ID: {} for event ID: {}", campaign.getId(), campaign.getEvent().getId());
        }
    }

    private void recoverStuckCampaigns(Instant now) {
        Instant stuckThreshold = now.minusSeconds(schedulerProperties.getStuckThresholdMinutes() * 60L);
        List<SmsCampaign> stuck = smsCampaignRepository.findStuckCampaigns(CampaignStatus.SENDING, stuckThreshold);

        for (SmsCampaign campaign : stuck) {
            if (campaign.getRetryCount() > schedulerProperties.getMaxRetryCount()) {
                campaign.setStatus(CampaignStatus.FAILED);
                smsCampaignRepository.save(campaign);
                log.error("Campaign ID: {} exceeded max retries ({}) — marked FAILED",
                        campaign.getId(), schedulerProperties.getMaxRetryCount());
            } else {
                campaign.setRetryCount(campaign.getRetryCount() + 1);
                smsCampaignRepository.save(campaign);
                smsCampaignSendService.sendCampaignAsync(campaign.getId());
                log.warn("Retrying stuck campaign ID: {} (attempt {})", campaign.getId(), campaign.getRetryCount());
            }
        }
    }
}
