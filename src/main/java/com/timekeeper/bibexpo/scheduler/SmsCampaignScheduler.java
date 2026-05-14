package com.timekeeper.bibexpo.scheduler;

import com.timekeeper.bibexpo.config.SmsSchedulerProperties;
import com.timekeeper.bibexpo.model.entity.SmsCampaign;
import com.timekeeper.bibexpo.model.enums.SmsCampaignStatus;
import com.timekeeper.bibexpo.model.enums.SmsCampaignTriggerType;
import com.timekeeper.bibexpo.repository.SmsCampaignRepository;
import com.timekeeper.bibexpo.service.SmsCampaignSendService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
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
        LocalDateTime now = LocalDateTime.now();

        fireDueCampaigns(now);
        recoverStuckCampaigns(now);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverOnStartup() {
        List<SmsCampaign> sending = smsCampaignRepository.findAllByStatus(SmsCampaignStatus.SENDING);
        if (sending.isEmpty()) {
            return;
        }
        log.info("Recovering {} SENDING campaign(s) found after startup", sending.size());
        for (SmsCampaign campaign : sending) {
            log.info("Recovering SENDING campaign ID: {} after restart", campaign.getId());
            smsCampaignSendService.sendCampaignAsync(campaign.getId());
        }
    }

    private void fireDueCampaigns(LocalDateTime now) {
        List<SmsCampaign> due = smsCampaignRepository.findDueCampaigns(
                SmsCampaignTriggerType.SCHEDULED, SmsCampaignStatus.ACTIVE, now);

        for (SmsCampaign campaign : due) {
            campaign.setStatus(SmsCampaignStatus.SENDING);
            smsCampaignRepository.save(campaign);
            smsCampaignSendService.sendCampaignAsync(campaign.getId());
            log.info("Fired scheduled campaign ID: {} for event ID: {}", campaign.getId(), campaign.getEvent().getId());
        }
    }

    private void recoverStuckCampaigns(LocalDateTime now) {
        LocalDateTime stuckThreshold = now.minusMinutes(schedulerProperties.getStuckThresholdMinutes());
        List<SmsCampaign> stuck = smsCampaignRepository.findStuckCampaigns(SmsCampaignStatus.SENDING, stuckThreshold);

        for (SmsCampaign campaign : stuck) {
            if (campaign.getRetryCount() > schedulerProperties.getMaxRetryCount()) {
                campaign.setStatus(SmsCampaignStatus.FAILED);
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
