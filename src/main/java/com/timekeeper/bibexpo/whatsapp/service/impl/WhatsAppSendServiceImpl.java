package com.timekeeper.bibexpo.whatsapp.service.impl;

import com.timekeeper.bibexpo.model.dynamodb.ParticipantDDB;
import com.timekeeper.bibexpo.model.entity.Event;
import com.timekeeper.bibexpo.model.enums.CampaignStatus;
import com.timekeeper.bibexpo.model.enums.CampaignTriggerType;
import com.timekeeper.bibexpo.service.util.RaceCategoryNameResolver;
import com.timekeeper.bibexpo.service.util.RaceCategoryNameResolver.EventNames;
import com.timekeeper.bibexpo.util.SmsTemplateContext;
import com.timekeeper.bibexpo.whatsapp.model.WhatsAppSender;
import com.timekeeper.bibexpo.whatsapp.model.entity.WhatsAppCampaign;
import com.timekeeper.bibexpo.whatsapp.model.entity.WhatsAppTemplate;
import com.timekeeper.bibexpo.whatsapp.repository.WhatsAppCampaignRepository;
import com.timekeeper.bibexpo.whatsapp.service.WhatsAppGatewayService;
import com.timekeeper.bibexpo.whatsapp.service.WhatsAppSendService;
import com.timekeeper.bibexpo.whatsapp.service.WhatsAppSenderResolver;
import com.timekeeper.bibexpo.whatsapp.service.util.WhatsAppVariableRenderer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsAppSendServiceImpl implements WhatsAppSendService {

    private final WhatsAppCampaignRepository campaignRepository;
    private final WhatsAppGatewayService gatewayService;
    private final WhatsAppSenderResolver senderResolver;
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
            Long organizationId = campaign.getOrganizationId();
            WhatsAppSender sender = senderResolver.resolve(organizationId);
            if (sender.getScope() != template.getSenderScope()) {
                log.warn("Skipping bib-collected WhatsApp for bib {} in event {}: template scope {} does not match current sender scope {}",
                        participant.getBibNumber(), eventId, template.getSenderScope(), sender.getScope());
                return;
            }

            EventNames names = nameResolver.forEvent(eventId);
            SmsTemplateContext context = new SmsTemplateContext(participant, event,
                    names.raceName(participant.getRaceId()), names.categoryName(participant.getCategoryId()));
            List<String> variables = WhatsAppVariableRenderer.render(template.getBodyVariables(), context);
            gatewayService.sendTemplate(sender, phone, template.getContentSid(), variables);
            log.info("Bib-collected WhatsApp sent to bib {} in event {}", participant.getBibNumber(), eventId);
        } catch (Exception e) {
            log.warn("Bib-collected WhatsApp failed for bib {} in event {}: {}", participant.getBibNumber(), eventId, e.getMessage());
        }
    }
}
