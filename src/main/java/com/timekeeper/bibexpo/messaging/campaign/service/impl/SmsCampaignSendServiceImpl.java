package com.timekeeper.bibexpo.messaging.campaign.service.impl;

import com.timekeeper.bibexpo.messaging.campaign.config.SmsSchedulerProperties;
import com.timekeeper.bibexpo.messaging.campaign.exception.SmsSendException;
import com.timekeeper.bibexpo.messaging.delivery.OutboundMessage;
import com.timekeeper.bibexpo.messaging.provider.exception.MessagingProviderException;
import com.timekeeper.bibexpo.messaging.provider.model.entity.MessagingProvider;
import com.timekeeper.bibexpo.messaging.provider.service.CampaignProviderResolver;
import com.timekeeper.bibexpo.messaging.provider.service.MessagingProviderClient;
import com.timekeeper.bibexpo.messaging.shared.enums.MessageChannel;
import com.timekeeper.bibexpo.model.dynamodb.ParticipantDDB;
import com.timekeeper.bibexpo.model.entity.Event;
import com.timekeeper.bibexpo.messaging.campaign.model.entity.SmsCampaign;
import com.timekeeper.bibexpo.messaging.campaign.model.enums.CampaignStatus;
import com.timekeeper.bibexpo.messaging.campaign.model.enums.CampaignTargetFilter;
import com.timekeeper.bibexpo.messaging.campaign.repository.SmsCampaignRepository;
import com.timekeeper.bibexpo.messaging.campaign.service.SmsCampaignSendService;
import com.timekeeper.bibexpo.messaging.campaign.util.CampaignCompatibilityGuard;
import com.timekeeper.bibexpo.messaging.campaign.util.CampaignDispatcher;
import com.timekeeper.bibexpo.messaging.campaign.util.CampaignNotifier;
import com.timekeeper.bibexpo.messaging.campaign.util.CampaignVariableRenderer;
import com.timekeeper.bibexpo.messaging.provider.service.impl.ProviderMappingValidator.TemplateContent;
import com.timekeeper.bibexpo.messaging.campaign.util.CampaignDispatcher.DispatchOutcome;
import com.timekeeper.bibexpo.messaging.campaign.util.CampaignDispatcher.DispatchRequest;
import com.timekeeper.bibexpo.service.util.RaceCategoryNameResolver;
import com.timekeeper.bibexpo.service.util.RaceCategoryNameResolver.EventNames;
import com.timekeeper.bibexpo.messaging.shared.template.MessageTemplateContext;
import com.timekeeper.bibexpo.messaging.shared.template.MessageTemplateParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SmsCampaignSendServiceImpl implements SmsCampaignSendService {

    private static final int CONSECUTIVE_FAILURE_THRESHOLD = 5;

    private final SmsCampaignRepository smsCampaignRepository;
    private final CampaignProviderResolver campaignProviderResolver;
    private final MessagingProviderClient messagingProviderClient;
    private final SmsSchedulerProperties schedulerProperties;
    private final RaceCategoryNameResolver nameResolver;
    private final CampaignDispatcher campaignDispatcher;
    private final CampaignNotifier campaignNotifier;
    private final CampaignCompatibilityGuard compatibilityGuard;

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
        String senderId = campaign.getSmsTemplate().getSenderId();
        String bodyVariables = campaign.getSmsTemplate().getBodyVariables();

        MessagingProvider provider;
        try {
            provider = campaignProviderResolver.resolve(MessageChannel.SMS, campaign.getOrganizationId());
        } catch (MessagingProviderException e) {
            campaign.setStatus(CampaignStatus.FAILED);
            smsCampaignRepository.save(campaign);
            log.error("SMS campaign ID: {} failed: {}", campaignId, e.getMessage());
            campaignNotifier.notifyFailed(campaignId, campaign.getName(), campaign.getOrganizationId(), "SMS");
            return;
        }

        // The sender may have changed since the campaign was armed, so the pairing is checked again
        // here — one bad dispatch bills the organization for messages with no content in them.
        Optional<String> incompatible = compatibilityGuard.problem(MessageChannel.SMS, campaign.getOrganizationId(),
                new TemplateContent(templateText != null && !templateText.isBlank(),
                        CampaignVariableRenderer.count(bodyVariables),
                        dltTemplateId != null && !dltTemplateId.isBlank(),
                        senderId != null && !senderId.isBlank()),
                campaign.getSmsTemplate().getProviderSource());
        if (incompatible.isPresent()) {
            campaign.setStatus(CampaignStatus.FAILED);
            smsCampaignRepository.save(campaign);
            log.error("SMS campaign ID: {} not sent — {}", campaignId, incompatible.get());
            campaignNotifier.notifyFailed(campaignId, campaign.getName(), campaign.getOrganizationId(), "SMS",
                    incompatible.get());
            return;
        }

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
                    MessageTemplateContext context = new MessageTemplateContext(participant, event,
                            names.raceName(participant.getRaceId()), names.categoryName(participant.getCategoryId()),
                            names.reportingTime(participant.getRaceId()));
                    String message = MessageTemplateParser.parse(templateText, context);
                    List<String> variables = CampaignVariableRenderer.render(bodyVariables, context);
                    messagingProviderClient.send(provider, OutboundMessage.builder()
                            .recipientPhone(participant.getPhoneNumber())
                            .templateId(dltTemplateId)
                            .senderId(senderId)
                            .message(message)
                            .variables(variables)
                            .build());
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
        campaignNotifier.notifyCompleted(campaignId, campaign.getName(), campaign.getOrganizationId(), "SMS", outcome.getSentCount());
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
