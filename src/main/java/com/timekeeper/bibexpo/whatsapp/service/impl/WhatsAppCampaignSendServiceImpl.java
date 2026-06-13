package com.timekeeper.bibexpo.whatsapp.service.impl;

import com.timekeeper.bibexpo.model.dynamodb.ParticipantDDB;
import com.timekeeper.bibexpo.model.entity.Event;
import com.timekeeper.bibexpo.model.enums.CampaignStatus;
import com.timekeeper.bibexpo.model.enums.CampaignTargetFilter;
import com.timekeeper.bibexpo.repository.EventRepository;
import com.timekeeper.bibexpo.service.util.CampaignDispatcher;
import com.timekeeper.bibexpo.service.util.CampaignDispatcher.DispatchOutcome;
import com.timekeeper.bibexpo.service.util.CampaignDispatcher.DispatchRequest;
import com.timekeeper.bibexpo.service.util.RaceCategoryNameResolver;
import com.timekeeper.bibexpo.service.util.RaceCategoryNameResolver.EventNames;
import com.timekeeper.bibexpo.util.SmsTemplateContext;
import com.timekeeper.bibexpo.whatsapp.config.WhatsAppSchedulerProperties;
import com.timekeeper.bibexpo.whatsapp.exception.WhatsAppSendException;
import com.timekeeper.bibexpo.whatsapp.model.WhatsAppSender;
import com.timekeeper.bibexpo.whatsapp.model.entity.WhatsAppCampaign;
import com.timekeeper.bibexpo.whatsapp.model.entity.WhatsAppTemplate;
import com.timekeeper.bibexpo.whatsapp.repository.WhatsAppCampaignRepository;
import com.timekeeper.bibexpo.whatsapp.service.WhatsAppCampaignSendService;
import com.timekeeper.bibexpo.whatsapp.service.WhatsAppGatewayService;
import com.timekeeper.bibexpo.whatsapp.service.WhatsAppSenderResolver;
import com.timekeeper.bibexpo.whatsapp.service.util.WhatsAppVariableRenderer;
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
    private final WhatsAppGatewayService gatewayService;
    private final WhatsAppSenderResolver senderResolver;
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
        Long organizationId = campaign.getOrganizationId();

        WhatsAppSender sender = senderResolver.resolve(organizationId);
        if (sender.getScope() != template.getSenderScope()) {
            campaign.setStatus(CampaignStatus.FAILED);
            campaignRepository.save(campaign);
            log.error("WhatsApp campaign ID: {} failed: template scope {} does not match current sender scope {} — " +
                            "the organization switched sender modes after arming",
                    campaignId, template.getSenderScope(), sender.getScope());
            return;
        }

        log.info("Starting async WhatsApp send for campaign ID: {} event ID: {} (sender scope: {})",
                campaignId, event.getId(), sender.getScope());

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
                    return gatewayService.sendTemplate(sender, participant.getPhoneNumber(), contentSid, variables);
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
