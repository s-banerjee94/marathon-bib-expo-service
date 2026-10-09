package com.timekeeper.bibexpo.messaging.campaign.service.impl;

import com.timekeeper.bibexpo.messaging.campaign.config.WhatsAppSchedulerProperties;
import com.timekeeper.bibexpo.messaging.campaign.exception.WhatsAppSendException;
import com.timekeeper.bibexpo.messaging.campaign.model.entity.WhatsAppCampaign;
import com.timekeeper.bibexpo.messaging.campaign.model.entity.WhatsAppTemplate;
import com.timekeeper.bibexpo.messaging.campaign.model.enums.CampaignStatus;
import com.timekeeper.bibexpo.messaging.campaign.model.enums.CampaignTargetFilter;
import com.timekeeper.bibexpo.messaging.campaign.repository.WhatsAppCampaignRepository;
import com.timekeeper.bibexpo.messaging.campaign.service.WhatsAppCampaignSendService;
import com.timekeeper.bibexpo.messaging.campaign.util.CampaignCompatibilityGuard;
import com.timekeeper.bibexpo.messaging.campaign.util.CampaignDispatcher.DispatchOutcome;
import com.timekeeper.bibexpo.messaging.campaign.util.CampaignDispatcher.DispatchRequest;
import com.timekeeper.bibexpo.messaging.campaign.util.CampaignDispatcher;
import com.timekeeper.bibexpo.messaging.campaign.util.CampaignNotifier;
import com.timekeeper.bibexpo.messaging.campaign.util.CampaignVariableRenderer;
import com.timekeeper.bibexpo.messaging.delivery.OutboundMessage;
import com.timekeeper.bibexpo.messaging.provider.exception.MessagingProviderException;
import com.timekeeper.bibexpo.messaging.provider.model.entity.MessagingProvider;
import com.timekeeper.bibexpo.messaging.provider.service.CampaignProviderResolver;
import com.timekeeper.bibexpo.messaging.provider.service.impl.ProviderMappingValidator.TemplateContent;
import com.timekeeper.bibexpo.messaging.provider.service.MessagingProviderClient;
import com.timekeeper.bibexpo.messaging.shared.enums.MessageChannel;
import com.timekeeper.bibexpo.messaging.shared.template.MessageTemplateContext;
import com.timekeeper.bibexpo.event.model.entity.Event;
import com.timekeeper.bibexpo.participant.model.dynamodb.ParticipantDDB;
import com.timekeeper.bibexpo.event.api.EventStore;
import com.timekeeper.bibexpo.event.api.EventNames;
import com.timekeeper.bibexpo.event.api.RaceCategoryNameQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsAppCampaignSendServiceImpl implements WhatsAppCampaignSendService {

    private static final int CONSECUTIVE_FAILURE_THRESHOLD = 5;

    private final WhatsAppCampaignRepository campaignRepository;
    private final EventStore eventStore;
    private final CampaignProviderResolver campaignProviderResolver;
    private final MessagingProviderClient messagingProviderClient;
    private final WhatsAppSchedulerProperties schedulerProperties;
    private final RaceCategoryNameQuery nameResolver;
    private final CampaignDispatcher campaignDispatcher;
    private final CampaignNotifier campaignNotifier;
    private final CampaignCompatibilityGuard compatibilityGuard;

    @Override
    @Async("whatsAppCampaignTaskExecutor")
    public void sendCampaignAsync(Long campaignId) {
        WhatsAppCampaign campaign = campaignRepository.findByIdWithDetails(campaignId).orElse(null);
        if (campaign == null) {
            logSkipped(campaignId, "campaign_not_found");
            return;
        }

        // The slice stores plain IDs; the event is fetched only for template-variable rendering
        Event event = eventStore.findById(campaign.getEventId()).orElse(null);
        if (event == null) {
            logSkipped(campaignId, "event_not_found");
            return;
        }
        WhatsAppTemplate template = campaign.getWhatsAppTemplate();

        MessagingProvider provider;
        try {
            provider = campaignProviderResolver.resolve(MessageChannel.WHATSAPP, campaign.getOrganizationId());
        } catch (MessagingProviderException e) {
            campaign.setStatus(CampaignStatus.FAILED);
            campaignRepository.save(campaign);
            logFailed(campaign, e.getMessage());
            campaignNotifier.notifyFailed(campaignId, campaign.getName(), campaign.getOrganizationId(), "WhatsApp");
            return;
        }

        String contentSid = template.getContentSid();
        String bodyVariables = template.getBodyVariables();

        // Re-checked here because the organization may have switched sender since the campaign was
        // armed, and a dispatch that cannot carry its content still costs per message.
        Optional<String> incompatible = compatibilityGuard.problem(MessageChannel.WHATSAPP, campaign.getOrganizationId(),
                new TemplateContent(false, CampaignVariableRenderer.count(bodyVariables),
                        contentSid != null && !contentSid.isBlank(), false),
                template.getProviderSource());
        if (incompatible.isPresent()) {
            campaign.setStatus(CampaignStatus.FAILED);
            campaignRepository.save(campaign);
            logFailed(campaign, incompatible.get());
            campaignNotifier.notifyFailed(campaignId, campaign.getName(), campaign.getOrganizationId(), "WhatsApp",
                    incompatible.get());
            return;
        }

        EventNames names = nameResolver.forEvent(event.getId());

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
                    MessageTemplateContext context = new MessageTemplateContext(participant, event,
                            names.raceName(participant.getRaceId()), names.categoryName(participant.getCategoryId()),
                            names.reportingTime(participant.getRaceId()));
                    List<String> variables = CampaignVariableRenderer.render(bodyVariables, context);
                    messagingProviderClient.send(provider, OutboundMessage.builder()
                            .recipientPhone(participant.getPhoneNumber())
                            .templateId(contentSid)
                            .variables(variables)
                            .build());
                    return null;
                })
                .checkpoint(sentCount -> campaignRepository.saveProgress(campaignId, sentCount, Instant.now()))
                .abortExceptionFactory(failures -> new WhatsAppSendException(
                        "Campaign " + campaignId + " aborted: " + failures + " consecutive gateway failures"))
                .build());

        if (!outcome.isCompleted()) {
            return;
        }

        campaign.setStatus(CampaignStatus.SENT);
        campaign.setSentCount(outcome.getSentCount());
        campaignRepository.save(campaign);
        log.atInfo()
                .addKeyValue("event", "campaign.completed")
                .addKeyValue("channel", MessageChannel.WHATSAPP)
                .addKeyValue("eventId", event.getId())
                .addKeyValue("campaignId", campaignId)
                .addKeyValue("sentCount", outcome.getSentCount())
                .log("Campaign completed");
        campaignNotifier.notifyCompleted(campaignId, campaign.getName(), campaign.getOrganizationId(), "WhatsApp", outcome.getSentCount());
    }

    private void logSkipped(Long campaignId, String reason) {
        log.atWarn()
                .addKeyValue("event", "campaign.send_skipped")
                .addKeyValue("channel", MessageChannel.WHATSAPP)
                .addKeyValue("campaignId", campaignId)
                .addKeyValue("reason", reason)
                .log("Campaign send skipped");
    }

    private void logFailed(WhatsAppCampaign campaign, String reason) {
        log.atError()
                .addKeyValue("event", "campaign.failed")
                .addKeyValue("channel", MessageChannel.WHATSAPP)
                .addKeyValue("eventId", campaign.getEventId())
                .addKeyValue("campaignId", campaign.getId())
                .addKeyValue("reason", reason)
                .log("Campaign failed");
    }

    private boolean matchesFilter(ParticipantDDB participant, CampaignTargetFilter filter) {
        if (filter == CampaignTargetFilter.ALL) {
            return true;
        }
        return participant.getBibCollectedAt() == null || participant.getBibCollectedAt().isBlank();
    }
}
