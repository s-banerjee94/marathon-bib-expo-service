package com.timekeeper.bibexpo.messaging.campaign.service.impl;

import com.timekeeper.bibexpo.event.api.EventCampaignUsage;
import com.timekeeper.bibexpo.messaging.campaign.repository.SmsCampaignRepository;
import com.timekeeper.bibexpo.messaging.campaign.repository.SmsTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EventCampaignUsageImpl implements EventCampaignUsage {

    private final SmsTemplateRepository smsTemplateRepository;
    private final SmsCampaignRepository smsCampaignRepository;

    @Override
    public long countSmsTemplates(Long eventId) {
        return smsTemplateRepository.countByEventId(eventId);
    }

    @Override
    public long countSmsCampaigns(Long eventId) {
        return smsCampaignRepository.countByEventId(eventId);
    }
}
