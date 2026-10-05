package com.timekeeper.bibexpo.messaging.campaign.service.impl;

import com.timekeeper.bibexpo.messaging.campaign.model.entity.SmsCampaign;
import com.timekeeper.bibexpo.messaging.campaign.model.entity.SmsTemplate;
import com.timekeeper.bibexpo.messaging.campaign.model.enums.CampaignStatus;
import com.timekeeper.bibexpo.messaging.campaign.model.enums.CampaignTriggerType;
import com.timekeeper.bibexpo.messaging.campaign.repository.SmsCampaignRepository;
import com.timekeeper.bibexpo.messaging.campaign.service.ParticipantEventSmsService;
import com.timekeeper.bibexpo.messaging.campaign.util.CampaignCompatibilityGuard;
import com.timekeeper.bibexpo.messaging.campaign.util.CampaignVariableRenderer;
import com.timekeeper.bibexpo.messaging.delivery.OutboundMessage;
import com.timekeeper.bibexpo.messaging.provider.model.entity.MessagingProvider;
import com.timekeeper.bibexpo.messaging.provider.service.CampaignProviderResolver;
import com.timekeeper.bibexpo.messaging.provider.service.impl.ProviderMappingValidator.TemplateContent;
import com.timekeeper.bibexpo.messaging.provider.service.MessagingProviderClient;
import com.timekeeper.bibexpo.messaging.shared.enums.MessageChannel;
import com.timekeeper.bibexpo.messaging.shared.template.MessageTemplateContext;
import com.timekeeper.bibexpo.messaging.shared.template.MessageTemplateParser;

import java.util.Optional;
import com.timekeeper.bibexpo.event.model.entity.Event;
import com.timekeeper.bibexpo.participant.model.dynamodb.ParticipantDDB;
import com.timekeeper.bibexpo.event.api.EventNames;
import com.timekeeper.bibexpo.event.api.RaceCategoryNameQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ParticipantEventSmsServiceImpl implements ParticipantEventSmsService {

    private final SmsCampaignRepository smsCampaignRepository;
    private final CampaignProviderResolver campaignProviderResolver;
    private final MessagingProviderClient messagingProviderClient;
    private final RaceCategoryNameQuery nameResolver;
    private final CampaignCompatibilityGuard compatibilityGuard;

    // Runs on its own thread after the collection is saved, so a slow provider never holds the counter;
    // nothing in here may fail the collection, so every failure is logged instead.
    @Async("bibCollectedMessageTaskExecutor")
    @Override
    public void sendBibCollectedSms(Event event, ParticipantDDB participant) {
        Long eventId = event.getId();
        try {
            SmsCampaign campaign = smsCampaignRepository
                    .findWithTemplateByEventIdAndTriggerTypeAndStatus(eventId, CampaignTriggerType.AUTO_BIB_COLLECTED, CampaignStatus.ACTIVE)
                    .orElse(null);
            if (campaign == null) {
                return;
            }

            String phone = participant.getPhoneNumber();
            if (phone == null || phone.isBlank()) {
                log.debug("Bib-collected SMS skipped: no phone number");
                return;
            }

            SmsTemplate template = campaign.getSmsTemplate();
            Optional<String> incompatible = compatibilityGuard.problem(MessageChannel.SMS, campaign.getOrganizationId(),
                    new TemplateContent(template.getTemplate() != null && !template.getTemplate().isBlank(),
                            CampaignVariableRenderer.count(template.getBodyVariables()),
                            template.getSmsTemplateId() != null && !template.getSmsTemplateId().isBlank(),
                            template.getSenderId() != null && !template.getSenderId().isBlank()),
                    template.getProviderSource());
            if (incompatible.isPresent()) {
                // Logged rather than notified: this fires per bib collection, and one notification per
                // participant would bury the counter staff.
                log.atError()
                        .addKeyValue("event", "campaign.auto_send_skipped")
                        .addKeyValue("channel", MessageChannel.SMS)
                        .addKeyValue("eventId", eventId)
                        .addKeyValue("campaignId", campaign.getId())
                        .addKeyValue("bibNumber", participant.getBibNumber())
                        .addKeyValue("reason", incompatible.get())
                        .log("Bib-collected message skipped");
                return;
            }

            MessagingProvider provider = campaignProviderResolver.resolve(MessageChannel.SMS, campaign.getOrganizationId());
            EventNames names = nameResolver.forEvent(eventId);
            MessageTemplateContext context = new MessageTemplateContext(participant, event,
                    names.raceName(participant.getRaceId()), names.categoryName(participant.getCategoryId()),
                    names.reportingTime(participant.getRaceId()));
            String renderedMessage = MessageTemplateParser.parse(template.getTemplate(), context);
            messagingProviderClient.send(provider, OutboundMessage.builder()
                    .recipientPhone(phone)
                    .templateId(template.getSmsTemplateId())
                    .senderId(template.getSenderId())
                    .message(renderedMessage)
                    .variables(CampaignVariableRenderer.render(template.getBodyVariables(), context))
                    .build());
            log.atInfo()
                    .addKeyValue("event", "campaign.auto_sent")
                    .addKeyValue("channel", MessageChannel.SMS)
                    .addKeyValue("eventId", eventId)
                    .addKeyValue("campaignId", campaign.getId())
                    .addKeyValue("bibNumber", participant.getBibNumber())
                    .log("Bib-collected message sent");
        } catch (Exception e) {
            log.atWarn()
                    .addKeyValue("event", "campaign.auto_send_failed")
                    .addKeyValue("channel", MessageChannel.SMS)
                    .addKeyValue("eventId", eventId)
                    .addKeyValue("bibNumber", participant.getBibNumber())
                    .addKeyValue("exception", e.getClass().getSimpleName())
                    .addKeyValue("reason", e.getMessage())
                    .log("Bib-collected message failed");
        }
    }
}
