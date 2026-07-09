package com.timekeeper.bibexpo.messaging.campaign.service.impl;

import com.timekeeper.bibexpo.messaging.delivery.OutboundMessage;
import com.timekeeper.bibexpo.messaging.provider.model.entity.MessagingProvider;
import com.timekeeper.bibexpo.messaging.provider.service.CampaignProviderResolver;
import com.timekeeper.bibexpo.messaging.provider.service.MessagingProviderClient;
import com.timekeeper.bibexpo.messaging.shared.enums.MessageChannel;
import com.timekeeper.bibexpo.model.dynamodb.ParticipantDDB;
import com.timekeeper.bibexpo.model.entity.Event;
import com.timekeeper.bibexpo.messaging.campaign.model.entity.SmsCampaign;
import com.timekeeper.bibexpo.messaging.campaign.model.enums.CampaignStatus;
import com.timekeeper.bibexpo.messaging.campaign.model.enums.CampaignTriggerType;
import com.timekeeper.bibexpo.messaging.campaign.repository.SmsCampaignRepository;
import com.timekeeper.bibexpo.messaging.campaign.service.ParticipantEventSmsService;
import com.timekeeper.bibexpo.service.util.RaceCategoryNameResolver;
import com.timekeeper.bibexpo.service.util.RaceCategoryNameResolver.EventNames;
import com.timekeeper.bibexpo.util.SmsTemplateContext;
import com.timekeeper.bibexpo.util.SmsTemplateParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ParticipantEventSmsServiceImpl implements ParticipantEventSmsService {

    private final SmsCampaignRepository smsCampaignRepository;
    private final CampaignProviderResolver campaignProviderResolver;
    private final MessagingProviderClient messagingProviderClient;
    private final RaceCategoryNameResolver nameResolver;

    @Override
    public void sendBibCollectedSms(Event event, ParticipantDDB participant) {
        Long eventId = event.getId();
        SmsCampaign campaign = smsCampaignRepository
                .findByEventIdAndTriggerTypeAndStatus(eventId, CampaignTriggerType.AUTO_BIB_COLLECTED, CampaignStatus.ACTIVE)
                .orElse(null);

        if (campaign == null) {
            log.info("No active AUTO_BIB_COLLECTED campaign for event ID: {} — skipping SMS", eventId);
            return;
        }

        String phone = participant.getPhoneNumber();
        if (phone == null || phone.isBlank()) {
            log.warn("Skipping bib-collected SMS for bib {} in event {}: no phone number", participant.getBibNumber(), eventId);
            return;
        }

        try {
            MessagingProvider provider = campaignProviderResolver.resolve(MessageChannel.SMS, campaign.getOrganizationId());
            EventNames names = nameResolver.forEvent(eventId);
            SmsTemplateContext context = new SmsTemplateContext(participant, event,
                    names.raceName(participant.getRaceId()), names.categoryName(participant.getCategoryId()),
                    names.reportingTime(participant.getRaceId()));
            String renderedMessage = SmsTemplateParser.parse(campaign.getSmsTemplate().getTemplate(), context);
            messagingProviderClient.send(provider, OutboundMessage.builder()
                    .recipientPhone(phone)
                    .templateId(campaign.getSmsTemplate().getSmsTemplateId())
                    .message(renderedMessage)
                    .build());
            log.info("Bib-collected SMS sent to bib {} in event {}", participant.getBibNumber(), eventId);
        } catch (Exception e) {
            log.warn("Bib-collected SMS failed for bib {} in event {}: {}", participant.getBibNumber(), eventId, e.getMessage());
        }
    }
}
