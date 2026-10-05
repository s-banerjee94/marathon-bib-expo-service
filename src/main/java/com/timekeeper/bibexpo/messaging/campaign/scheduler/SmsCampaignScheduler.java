package com.timekeeper.bibexpo.messaging.campaign.scheduler;

import com.timekeeper.bibexpo.messaging.campaign.config.SmsSchedulerProperties;
import com.timekeeper.bibexpo.messaging.campaign.model.entity.SmsCampaign;
import com.timekeeper.bibexpo.messaging.campaign.model.enums.CampaignStatus;
import com.timekeeper.bibexpo.messaging.campaign.model.enums.CampaignTriggerType;
import com.timekeeper.bibexpo.messaging.campaign.repository.SmsCampaignRepository;
import com.timekeeper.bibexpo.messaging.campaign.service.SmsCampaignSendService;
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
public class SmsCampaignScheduler {

    private final SmsCampaignRepository smsCampaignRepository;
    private final SmsCampaignSendService smsCampaignSendService;
    private final SmsSchedulerProperties schedulerProperties;
    private final CampaignNotifier campaignNotifier;

    @Scheduled(fixedDelay = 60_000)
    public void tick() {
        Instant now = Instant.now();

        fireDueCampaigns(now);
        recoverStuckCampaigns(now);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverOnStartup() {
        for (SmsCampaign campaign : smsCampaignRepository.findAllByStatus(CampaignStatus.SENDING)) {
            log.atInfo()
                    .addKeyValue("event", "campaign.resumed")
                    .addKeyValue("channel", MessageChannel.SMS)
                    .addKeyValue("eventId", campaign.getEvent().getId())
                    .addKeyValue("campaignId", campaign.getId())
                    .log("Campaign resumed after startup");
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
            log.atInfo()
                    .addKeyValue("event", "campaign.fired")
                    .addKeyValue("channel", MessageChannel.SMS)
                    .addKeyValue("eventId", campaign.getEvent().getId())
                    .addKeyValue("campaignId", campaign.getId())
                    .log("Scheduled campaign fired");
        }
    }

    private void recoverStuckCampaigns(Instant now) {
        Instant stuckThreshold = now.minusSeconds(schedulerProperties.getStuckThresholdMinutes() * 60L);
        List<SmsCampaign> stuck = smsCampaignRepository.findStuckCampaigns(CampaignStatus.SENDING, stuckThreshold);

        for (SmsCampaign campaign : stuck) {
            if (campaign.getRetryCount() >= schedulerProperties.getMaxRetryCount()) {
                campaign.setStatus(CampaignStatus.FAILED);
                smsCampaignRepository.save(campaign);
                log.atError()
                        .addKeyValue("event", "campaign.failed")
                        .addKeyValue("channel", MessageChannel.SMS)
                        .addKeyValue("eventId", campaign.getEvent().getId())
                        .addKeyValue("campaignId", campaign.getId())
                        .addKeyValue("reason", "retries_exhausted")
                        .addKeyValue("retryCount", campaign.getRetryCount())
                        .log("Campaign failed");
                campaignNotifier.notifyFailed(campaign.getId(), campaign.getName(), campaign.getOrganizationId(), "SMS");
            } else {
                campaign.setRetryCount(campaign.getRetryCount() + 1);
                smsCampaignRepository.save(campaign);
                smsCampaignSendService.sendCampaignAsync(campaign.getId());
                log.atWarn()
                        .addKeyValue("event", "campaign.retried")
                        .addKeyValue("channel", MessageChannel.SMS)
                        .addKeyValue("eventId", campaign.getEvent().getId())
                        .addKeyValue("campaignId", campaign.getId())
                        .addKeyValue("retryCount", campaign.getRetryCount())
                        .log("Stuck campaign retried");
            }
        }
    }
}
