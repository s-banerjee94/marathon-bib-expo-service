package com.timekeeper.bibexpo.service.impl;

import com.timekeeper.bibexpo.model.dynamodb.ParticipantDDB;
import com.timekeeper.bibexpo.model.entity.Event;
import com.timekeeper.bibexpo.model.entity.SmsCampaign;
import com.timekeeper.bibexpo.model.enums.SmsCampaignStatus;
import com.timekeeper.bibexpo.model.enums.SmsCampaignTriggerType;
import com.timekeeper.bibexpo.repository.SmsCampaignRepository;
import com.timekeeper.bibexpo.service.SmsGatewayService;
import com.timekeeper.bibexpo.service.SmsSendService;
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
public class SmsSendServiceImpl implements SmsSendService {

    private final SmsCampaignRepository smsCampaignRepository;
    private final SmsGatewayService smsGatewayService;
    private final RaceCategoryNameResolver nameResolver;

    @Override
    public void sendBibCollectedSms(Event event, ParticipantDDB participant) {
        Long eventId = event.getId();
        SmsCampaign campaign = smsCampaignRepository
                .findByEventIdAndTriggerTypeAndStatus(eventId, SmsCampaignTriggerType.AUTO_BIB_COLLECTED, SmsCampaignStatus.ACTIVE)
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
            EventNames names = nameResolver.forEvent(eventId);
            SmsTemplateContext context = new SmsTemplateContext(participant, event,
                    names.raceName(participant.getRaceId()), names.categoryName(participant.getCategoryId()));
            String renderedMessage = SmsTemplateParser.parse(campaign.getSmsTemplate().getTemplate(), context);
            smsGatewayService.send(phone, renderedMessage, campaign.getSmsTemplate().getSmsTemplateId());
            log.info("Bib-collected SMS sent to bib {} in event {}", participant.getBibNumber(), eventId);
        } catch (Exception e) {
            log.warn("Bib-collected SMS failed for bib {} in event {}: {}", participant.getBibNumber(), eventId, e.getMessage());
        }
    }
}
