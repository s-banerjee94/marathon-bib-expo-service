package com.timekeeper.bibexpo.service.impl;

import com.timekeeper.bibexpo.config.SmsSchedulerProperties;
import com.timekeeper.bibexpo.exception.SmsSendException;
import com.timekeeper.bibexpo.model.dynamodb.ParticipantDDB;
import com.timekeeper.bibexpo.model.entity.Event;
import com.timekeeper.bibexpo.model.entity.SmsCampaign;
import com.timekeeper.bibexpo.model.enums.CampaignStatus;
import com.timekeeper.bibexpo.model.enums.CampaignTargetFilter;
import com.timekeeper.bibexpo.repository.SmsCampaignRepository;
import com.timekeeper.bibexpo.service.SmsCampaignSendService;
import com.timekeeper.bibexpo.service.SmsGatewayService;
import com.timekeeper.bibexpo.service.util.CampaignDispatcher;
import com.timekeeper.bibexpo.service.util.CampaignDispatcher.DispatchOutcome;
import com.timekeeper.bibexpo.service.util.CampaignDispatcher.DispatchRequest;
import com.timekeeper.bibexpo.service.util.RaceCategoryNameResolver;
import com.timekeeper.bibexpo.service.util.RaceCategoryNameResolver.EventNames;
import com.timekeeper.bibexpo.util.SmsTemplateContext;
import com.timekeeper.bibexpo.util.SmsTemplateParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SmsCampaignSendServiceImpl implements SmsCampaignSendService {

    private static final int CONSECUTIVE_FAILURE_THRESHOLD = 5;

    private final SmsCampaignRepository smsCampaignRepository;
    private final SmsGatewayService smsGatewayService;
    private final SmsSchedulerProperties schedulerProperties;
    private final RaceCategoryNameResolver nameResolver;
    private final CampaignDispatcher campaignDispatcher;

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

        log.info("Starting async SMS send for campaign ID: {} event ID: {}", campaignId, event.getId());

        EventNames names = nameResolver.forEvent(event.getId());

        DispatchOutcome outcome = campaignDispatcher.dispatch(DispatchRequest.builder()
                .eventId(event.getId())
                .campaignKey(campaignId.toString())
                .channelName("SMS")
                .initialSentCount(campaign.getSentCount())
                .sendDelayMs(schedulerProperties.getSendDelayMs())
                .consecutiveFailureThreshold(CONSECUTIVE_FAILURE_THRESHOLD)
                .targetFilter(participant -> matchesFilter(participant, campaign.getTargetFilter()))
                .sendsMapAccessor(ParticipantDDB::getSmsCampaignSends)
                .sender(participant -> {
                    SmsTemplateContext context = new SmsTemplateContext(participant, event,
                            names.raceName(participant.getRaceId()), names.categoryName(participant.getCategoryId()),
                            names.reportingTime(participant.getRaceId()));
                    String message = SmsTemplateParser.parse(templateText, context);
                    smsGatewayService.send(participant.getPhoneNumber(), message, dltTemplateId);
                    return null;
                })
                .checkpoint(sentCount -> saveSentCount(campaign, sentCount))
                .abortExceptionFactory(failures -> new SmsSendException(
                        "Campaign " + campaignId + " aborted: " + failures + " consecutive gateway failures"))
                .build());

        if (!outcome.isCompleted()) {
            return;
        }

        campaign.setStatus(CampaignStatus.SENT);
        campaign.setSentCount(outcome.getSentCount());
        smsCampaignRepository.save(campaign);
        log.info("Campaign ID: {} completed — {} SMS sent", campaignId, outcome.getSentCount());
    }

    private void saveSentCount(SmsCampaign campaign, int sentCount) {
        campaign.setSentCount(sentCount);
        smsCampaignRepository.save(campaign);
    }

    private boolean matchesFilter(ParticipantDDB participant, CampaignTargetFilter filter) {
        if (filter == CampaignTargetFilter.ALL) {
            return true;
        }
        return participant.getBibCollectedAt() == null || participant.getBibCollectedAt().isBlank();
    }
}
