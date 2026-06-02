package com.timekeeper.bibexpo.service.impl;

import com.timekeeper.bibexpo.config.SmsSchedulerProperties;
import com.timekeeper.bibexpo.exception.SmsSendException;
import com.timekeeper.bibexpo.model.dynamodb.ParticipantDDB;
import com.timekeeper.bibexpo.model.entity.Event;
import com.timekeeper.bibexpo.model.entity.SmsCampaign;
import com.timekeeper.bibexpo.model.enums.SmsCampaignStatus;
import com.timekeeper.bibexpo.model.enums.SmsCampaignTargetFilter;
import com.timekeeper.bibexpo.repository.SmsCampaignRepository;
import com.timekeeper.bibexpo.repository.dynamodb.ParticipantDDBRepository;
import com.timekeeper.bibexpo.service.SmsCampaignSendService;
import com.timekeeper.bibexpo.service.SmsGatewayService;
import com.timekeeper.bibexpo.service.util.RaceCategoryNameResolver;
import com.timekeeper.bibexpo.service.util.RaceCategoryNameResolver.EventNames;
import com.timekeeper.bibexpo.util.SmsTemplateContext;
import com.timekeeper.bibexpo.util.SmsTemplateParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import software.amazon.awssdk.enhanced.dynamodb.model.Page;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class SmsCampaignSendServiceImpl implements SmsCampaignSendService {

    private static final int CONSECUTIVE_FAILURE_THRESHOLD = 5;

    private final SmsCampaignRepository smsCampaignRepository;
    private final ParticipantDDBRepository participantDDBRepository;
    private final SmsGatewayService smsGatewayService;
    private final SmsSchedulerProperties schedulerProperties;
    private final RaceCategoryNameResolver nameResolver;

    @Override
    @Async("smsCampaignTaskExecutor")
    public void sendCampaignAsync(Long campaignId) {
        SmsCampaign campaign = smsCampaignRepository.findByIdWithDetails(campaignId).orElse(null);
        if (campaign == null) {
            log.warn("Campaign ID: {} not found during async send — skipping", campaignId);
            return;
        }

        Event event = campaign.getEvent();
        String templateText = campaign.getSmsTemplate().getTemplate();
        String dltTemplateId = campaign.getSmsTemplate().getSmsTemplateId();
        String campaignIdStr = campaignId.toString();

        log.info("Starting async SMS send for campaign ID: {} event ID: {}", campaignId, event.getId());

        EventNames names = nameResolver.forEvent(event.getId());
        int sentCount = campaign.getSentCount();
        int consecutiveFailures = 0;

        outer:
        for (Page<ParticipantDDB> page : participantDDBRepository.findPagesByEventId(event.getId(), 50)) {
            for (ParticipantDDB participant : page.items()) {
                if (!matchesFilter(participant, campaign.getTargetFilter())) {
                    continue;
                }
                if (participant.getSmsCampaignSends().containsKey(campaignIdStr)) {
                    log.debug("Skipping already-sent participant bib {} for campaign {}", participant.getBibNumber(), campaignId);
                    continue;
                }
                String phone = participant.getPhoneNumber();
                if (phone == null || phone.isBlank()) {
                    log.warn("Skipping bib {} for campaign {}: no phone number", participant.getBibNumber(), campaignId);
                    continue;
                }

                try {
                    Thread.sleep(schedulerProperties.getSendDelayMs());

                    SmsTemplateContext context = new SmsTemplateContext(participant, event,
                            names.raceName(participant.getRaceId()), names.categoryName(participant.getCategoryId()));
                    String message = SmsTemplateParser.parse(templateText, context);
                    smsGatewayService.send(phone, message, dltTemplateId);

                    participant.getSmsCampaignSends().put(campaignIdStr, Instant.now().toString());
                    participantDDBRepository.save(participant);
                    sentCount++;
                    consecutiveFailures = 0;
                    log.debug("SMS sent to bib {} for campaign {}", participant.getBibNumber(), campaignId);

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    saveSentCount(campaign, sentCount);
                    log.warn("Campaign ID: {} send interrupted at bib {}", campaignId, participant.getBibNumber());
                    return;

                } catch (Exception e) {
                    consecutiveFailures++;
                    log.warn("SMS failed for bib {} in campaign {}: {} (consecutive: {})",
                            participant.getBibNumber(), campaignId, e.getMessage(), consecutiveFailures);

                    if (consecutiveFailures >= CONSECUTIVE_FAILURE_THRESHOLD) {
                        saveSentCount(campaign, sentCount);
                        log.error("Campaign ID: {} aborted after {} consecutive gateway failures — scheduler will retry", campaignId, consecutiveFailures);
                        throw new SmsSendException("Campaign " + campaignId + " aborted: " + consecutiveFailures + " consecutive gateway failures");
                    }
                }
            }
        }

        campaign.setStatus(SmsCampaignStatus.SENT);
        campaign.setSentCount(sentCount);
        smsCampaignRepository.save(campaign);
        log.info("Campaign ID: {} completed — {} SMS sent", campaignId, sentCount);
    }

    private void saveSentCount(SmsCampaign campaign, int sentCount) {
        campaign.setSentCount(sentCount);
        smsCampaignRepository.save(campaign);
    }

    private boolean matchesFilter(ParticipantDDB participant, SmsCampaignTargetFilter filter) {
        if (filter == SmsCampaignTargetFilter.ALL) {
            return true;
        }
        return participant.getBibCollectedAt() == null || participant.getBibCollectedAt().isBlank();
    }
}
