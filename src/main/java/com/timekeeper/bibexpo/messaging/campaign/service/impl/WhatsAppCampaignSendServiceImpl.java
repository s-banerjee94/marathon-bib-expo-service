package com.timekeeper.bibexpo.messaging.campaign.service.impl;

import com.timekeeper.bibexpo.messaging.delivery.OutboundMessage;
import com.timekeeper.bibexpo.messaging.provider.exception.MessagingProviderException;
import com.timekeeper.bibexpo.messaging.provider.model.entity.MessagingProvider;
import com.timekeeper.bibexpo.messaging.provider.service.CampaignProviderResolver;
import com.timekeeper.bibexpo.messaging.provider.service.MessagingProviderClient;
import com.timekeeper.bibexpo.messaging.shared.enums.MessageChannel;
import com.timekeeper.bibexpo.model.dynamodb.ParticipantDDB;
import com.timekeeper.bibexpo.model.entity.Event;
import com.timekeeper.bibexpo.messaging.campaign.model.enums.CampaignStatus;
import com.timekeeper.bibexpo.messaging.campaign.model.enums.CampaignTargetFilter;
import com.timekeeper.bibexpo.repository.EventRepository;
import com.timekeeper.bibexpo.messaging.campaign.util.CampaignDispatcher;
import com.timekeeper.bibexpo.messaging.campaign.util.CampaignDispatcher.DispatchOutcome;
import com.timekeeper.bibexpo.messaging.campaign.util.CampaignDispatcher.DispatchRequest;
import com.timekeeper.bibexpo.service.util.RaceCategoryNameResolver;
import com.timekeeper.bibexpo.service.util.RaceCategoryNameResolver.EventNames;
import com.timekeeper.bibexpo.util.SmsTemplateContext;
import com.timekeeper.bibexpo.messaging.campaign.config.WhatsAppSchedulerProperties;
import com.timekeeper.bibexpo.messaging.campaign.exception.WhatsAppSendException;
import com.timekeeper.bibexpo.messaging.campaign.model.entity.WhatsAppCampaign;
import com.timekeeper.bibexpo.messaging.campaign.model.entity.WhatsAppTemplate;
import com.timekeeper.bibexpo.messaging.campaign.repository.WhatsAppCampaignRepository;
import com.timekeeper.bibexpo.messaging.campaign.service.WhatsAppCampaignSendService;
import com.timekeeper.bibexpo.messaging.campaign.util.WhatsAppVariableRenderer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsAppCampaignSendServiceImpl implements WhatsAppCampaignSendService {

    private static final int CONSECUTIVE_FAILURE_THRESHOLD = 5;

    private final WhatsAppCampaignRepository campaignRepository;
    private final EventRepository eventRepository;
    private final CampaignProviderResolver campaignProviderResolver;
    private final MessagingProviderClient messagingProviderClient;
    private final WhatsAppSchedulerProperties schedulerProperties;
    private final RaceCategoryNameResolver nameResolver;
    private final CampaignDispatcher campaignDispatcher;

    @Override
    @Async("whatsAppCampaignTaskExecutor")
    public void sendCampaignAsync(Long campaignId) {
        WhatsAppCampaign campaign = campaignRepository.findByIdWithDetails(campaignId).orElse(null);
        if (campaign == null) {
            log.warn("WhatsApp campaign ID: {} not found during async send — skipping", campaignId);
            return;
        }

        // The slice stores plain IDs; the event is fetched only for template-variable rendering
        Event event = eventRepository.findById(campaign.getEventId()).orElse(null);
        if (event == null) {
            log.warn("Event ID: {} not found for WhatsApp campaign ID: {} — skipping", campaign.getEventId(), campaignId);
            return;
        }
        WhatsAppTemplate template = campaign.getWhatsAppTemplate();

        MessagingProvider provider;
        try {
            provider = campaignProviderResolver.resolve(MessageChannel.WHATSAPP, campaign.getOrganizationId());
        } catch (MessagingProviderException e) {
            campaign.setStatus(CampaignStatus.FAILED);
            campaignRepository.save(campaign);
            log.error("WhatsApp campaign ID: {} failed: {}", campaignId, e.getMessage());
            return;
        }

        log.info("Starting async WhatsApp send for campaign ID: {} event ID: {}", campaignId, event.getId());

        EventNames names = nameResolver.forEvent(event.getId());
        String contentSid = template.getContentSid();
        String bodyVariables = template.getBodyVariables();

        DispatchOutcome outcome = campaignDispatcher.dispatch(DispatchRequest.builder()
                .eventId(event.getId())
                .campaignKey(campaignId.toString())
                .channelName("WhatsApp")
                .initialSentCount(campaign.getSentCount())
                .sendDelayMs(schedulerProperties.getSendDelayMs())
                .consecutiveFailureThreshold(CONSECUTIVE_FAILURE_THRESHOLD)
                .targetFilter(participant -> matchesFilter(participant, campaign.getTargetFilter()))
                .sendsMapAccessor(ParticipantDDB::getWhatsAppCampaignSends)
                .sender(participant -> {
                    SmsTemplateContext context = new SmsTemplateContext(participant, event,
                            names.raceName(participant.getRaceId()), names.categoryName(participant.getCategoryId()),
                            names.reportingTime(participant.getRaceId()));
                    List<String> variables = WhatsAppVariableRenderer.render(bodyVariables, context);
                    messagingProviderClient.send(provider, OutboundMessage.builder()
                            .recipientPhone(participant.getPhoneNumber())
                            .templateId(contentSid)
                            .variables(variables)
                            .build());
                    return null;
                })
                .checkpoint(sentCount -> saveSentCount(campaign, sentCount))
                .abortExceptionFactory(failures -> new WhatsAppSendException(
                        "Campaign " + campaignId + " aborted: " + failures + " consecutive gateway failures"))
                .build());

        if (!outcome.isCompleted()) {
            return;
        }

        campaign.setStatus(CampaignStatus.SENT);
        campaign.setSentCount(outcome.getSentCount());
        campaignRepository.save(campaign);
        log.info("WhatsApp campaign ID: {} completed — {} messages sent", campaignId, outcome.getSentCount());
    }

    private void saveSentCount(WhatsAppCampaign campaign, int sentCount) {
        campaign.setSentCount(sentCount);
        campaignRepository.save(campaign);
    }

    private boolean matchesFilter(ParticipantDDB participant, CampaignTargetFilter filter) {
        if (filter == CampaignTargetFilter.ALL) {
            return true;
        }
        return participant.getBibCollectedAt() == null || participant.getBibCollectedAt().isBlank();
    }
}
