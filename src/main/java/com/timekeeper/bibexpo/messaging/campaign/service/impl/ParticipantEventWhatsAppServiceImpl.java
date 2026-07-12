package com.timekeeper.bibexpo.messaging.campaign.service.impl;

import com.timekeeper.bibexpo.messaging.delivery.OutboundMessage;
import com.timekeeper.bibexpo.messaging.provider.model.entity.MessagingProvider;
import com.timekeeper.bibexpo.messaging.provider.service.CampaignProviderResolver;
import com.timekeeper.bibexpo.messaging.provider.service.MessagingProviderClient;
import com.timekeeper.bibexpo.messaging.shared.enums.MessageChannel;
import com.timekeeper.bibexpo.model.dynamodb.ParticipantDDB;
import com.timekeeper.bibexpo.model.entity.Event;
import com.timekeeper.bibexpo.messaging.campaign.model.enums.CampaignStatus;
import com.timekeeper.bibexpo.messaging.campaign.model.enums.CampaignTriggerType;
import com.timekeeper.bibexpo.service.util.RaceCategoryNameResolver;
import com.timekeeper.bibexpo.service.util.RaceCategoryNameResolver.EventNames;
import com.timekeeper.bibexpo.messaging.shared.template.MessageTemplateContext;
import com.timekeeper.bibexpo.messaging.campaign.model.entity.WhatsAppCampaign;
import com.timekeeper.bibexpo.messaging.campaign.model.entity.WhatsAppTemplate;
import com.timekeeper.bibexpo.messaging.campaign.repository.WhatsAppCampaignRepository;
import com.timekeeper.bibexpo.messaging.campaign.service.ParticipantEventWhatsAppService;
import com.timekeeper.bibexpo.messaging.campaign.util.WhatsAppVariableRenderer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ParticipantEventWhatsAppServiceImpl implements ParticipantEventWhatsAppService {

    private final WhatsAppCampaignRepository campaignRepository;
    private final CampaignProviderResolver campaignProviderResolver;
    private final MessagingProviderClient messagingProviderClient;
    private final RaceCategoryNameResolver nameResolver;

    @Override
    public void sendBibCollectedWhatsApp(Event event, ParticipantDDB participant) {
        Long eventId = event.getId();
        WhatsAppCampaign campaign = campaignRepository
                .findByEventIdAndTriggerTypeAndStatus(eventId, CampaignTriggerType.AUTO_BIB_COLLECTED, CampaignStatus.ACTIVE)
                .orElse(null);

        if (campaign == null) {
            log.info("No active AUTO_BIB_COLLECTED WhatsApp campaign for event ID: {} — skipping", eventId);
            return;
        }

        String phone = participant.getPhoneNumber();
        if (phone == null || phone.isBlank()) {
            log.warn("Skipping bib-collected WhatsApp for bib {} in event {}: no phone number", participant.getBibNumber(), eventId);
            return;
        }

        try {
            WhatsAppTemplate template = campaign.getWhatsAppTemplate();
            MessagingProvider provider = campaignProviderResolver.resolve(MessageChannel.WHATSAPP, campaign.getOrganizationId());

            EventNames names = nameResolver.forEvent(eventId);
            MessageTemplateContext context = new MessageTemplateContext(participant, event,
                    names.raceName(participant.getRaceId()), names.categoryName(participant.getCategoryId()),
                    names.reportingTime(participant.getRaceId()));
            List<String> variables = WhatsAppVariableRenderer.render(template.getBodyVariables(), context);
            messagingProviderClient.send(provider, OutboundMessage.builder()
                    .recipientPhone(phone)
                    .templateId(template.getContentSid())
                    .variables(variables)
                    .build());
            log.info("Bib-collected WhatsApp sent to bib {} in event {}", participant.getBibNumber(), eventId);
        } catch (Exception e) {
            log.warn("Bib-collected WhatsApp failed for bib {} in event {}: {}", participant.getBibNumber(), eventId, e.getMessage());
        }
    }
}
