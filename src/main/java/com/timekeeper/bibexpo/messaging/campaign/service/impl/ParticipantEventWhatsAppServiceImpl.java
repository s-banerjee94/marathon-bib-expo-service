package com.timekeeper.bibexpo.messaging.campaign.service.impl;

import com.timekeeper.bibexpo.messaging.campaign.model.entity.WhatsAppCampaign;
import com.timekeeper.bibexpo.messaging.campaign.model.entity.WhatsAppTemplate;
import com.timekeeper.bibexpo.messaging.campaign.model.enums.CampaignStatus;
import com.timekeeper.bibexpo.messaging.campaign.model.enums.CampaignTriggerType;
import com.timekeeper.bibexpo.messaging.campaign.repository.WhatsAppCampaignRepository;
import com.timekeeper.bibexpo.messaging.campaign.service.ParticipantEventWhatsAppService;
import com.timekeeper.bibexpo.messaging.campaign.util.CampaignCompatibilityGuard;
import com.timekeeper.bibexpo.messaging.campaign.util.CampaignVariableRenderer;
import com.timekeeper.bibexpo.messaging.delivery.OutboundMessage;
import com.timekeeper.bibexpo.messaging.provider.model.entity.MessagingProvider;
import com.timekeeper.bibexpo.messaging.provider.service.CampaignProviderResolver;
import com.timekeeper.bibexpo.messaging.provider.service.impl.ProviderMappingValidator.TemplateContent;
import com.timekeeper.bibexpo.messaging.provider.service.MessagingProviderClient;
import com.timekeeper.bibexpo.messaging.shared.enums.MessageChannel;
import com.timekeeper.bibexpo.messaging.shared.template.MessageTemplateContext;
import com.timekeeper.bibexpo.event.model.entity.Event;
import com.timekeeper.bibexpo.participant.model.dynamodb.ParticipantDDB;
import com.timekeeper.bibexpo.event.api.EventNames;
import com.timekeeper.bibexpo.event.api.RaceCategoryNameQuery;

import java.util.Optional;
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
    private final RaceCategoryNameQuery nameResolver;
    private final CampaignCompatibilityGuard compatibilityGuard;

    // Runs after the collection is saved: nothing in here may fail the counter, so every failure is logged instead.
    @Override
    public void sendBibCollectedWhatsApp(Event event, ParticipantDDB participant) {
        Long eventId = event.getId();
        try {
            WhatsAppCampaign campaign = campaignRepository
                    .findByEventIdAndTriggerTypeAndStatus(eventId, CampaignTriggerType.AUTO_BIB_COLLECTED, CampaignStatus.ACTIVE)
                    .orElse(null);
            if (campaign == null) {
                return;
            }

            String phone = participant.getPhoneNumber();
            if (phone == null || phone.isBlank()) {
                log.debug("Bib-collected WhatsApp skipped: no phone number");
                return;
            }

            WhatsAppTemplate template = campaign.getWhatsAppTemplate();
            Optional<String> incompatible = compatibilityGuard.problem(MessageChannel.WHATSAPP, campaign.getOrganizationId(),
                    new TemplateContent(false, CampaignVariableRenderer.count(template.getBodyVariables()),
                            template.getContentSid() != null && !template.getContentSid().isBlank(), false),
                    template.getProviderSource());
            if (incompatible.isPresent()) {
                // Logged rather than notified: this fires per bib collection, and one notification per
                // participant would bury the counter staff.
                log.atError()
                        .addKeyValue("event", "campaign.auto_send_skipped")
                        .addKeyValue("channel", MessageChannel.WHATSAPP)
                        .addKeyValue("eventId", eventId)
                        .addKeyValue("campaignId", campaign.getId())
                        .addKeyValue("bibNumber", participant.getBibNumber())
                        .addKeyValue("reason", incompatible.get())
                        .log("Bib-collected message skipped");
                return;
            }

            MessagingProvider provider = campaignProviderResolver.resolve(MessageChannel.WHATSAPP, campaign.getOrganizationId());

            EventNames names = nameResolver.forEvent(eventId);
            MessageTemplateContext context = new MessageTemplateContext(participant, event,
                    names.raceName(participant.getRaceId()), names.categoryName(participant.getCategoryId()),
                    names.reportingTime(participant.getRaceId()));
            List<String> variables = CampaignVariableRenderer.render(template.getBodyVariables(), context);
            messagingProviderClient.send(provider, OutboundMessage.builder()
                    .recipientPhone(phone)
                    .templateId(template.getContentSid())
                    .variables(variables)
                    .build());
            log.atInfo()
                    .addKeyValue("event", "campaign.auto_sent")
                    .addKeyValue("channel", MessageChannel.WHATSAPP)
                    .addKeyValue("eventId", eventId)
                    .addKeyValue("campaignId", campaign.getId())
                    .addKeyValue("bibNumber", participant.getBibNumber())
                    .log("Bib-collected message sent");
        } catch (Exception e) {
            log.atWarn()
                    .addKeyValue("event", "campaign.auto_send_failed")
                    .addKeyValue("channel", MessageChannel.WHATSAPP)
                    .addKeyValue("eventId", eventId)
                    .addKeyValue("bibNumber", participant.getBibNumber())
                    .addKeyValue("exception", e.getClass().getSimpleName())
                    .addKeyValue("reason", e.getMessage())
                    .log("Bib-collected message failed");
        }
    }
}
