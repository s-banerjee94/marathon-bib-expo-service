package com.timekeeper.bibexpo.messaging.campaign.service.impl;

import com.timekeeper.bibexpo.messaging.campaign.repository.SmsCampaignRepository;
import com.timekeeper.bibexpo.messaging.campaign.repository.SmsTemplateRepository;
import com.timekeeper.bibexpo.messaging.campaign.repository.WhatsAppCampaignRepository;
import com.timekeeper.bibexpo.messaging.campaign.repository.WhatsAppTemplateRepository;
import com.timekeeper.bibexpo.service.EventDeletionGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Messaging-side implementation of the core {@link EventDeletionGuard} port: blocks event
 * deletion while campaign templates or campaigns still reference the event.
 */
@Service
@RequiredArgsConstructor
public class MessagingEventDeletionGuard implements EventDeletionGuard {

    private final SmsTemplateRepository smsTemplateRepository;
    private final WhatsAppTemplateRepository whatsAppTemplateRepository;
    private final SmsCampaignRepository smsCampaignRepository;
    private final WhatsAppCampaignRepository whatsAppCampaignRepository;

    @Override
    public Optional<String> findBlockingContent(Long eventId) {
        if (smsTemplateRepository.countByEventId(eventId) > 0) {
            return Optional.of("SMS templates");
        }
        if (whatsAppTemplateRepository.countByEventId(eventId) > 0) {
            return Optional.of("WhatsApp templates");
        }
        if (smsCampaignRepository.countByEventId(eventId) > 0) {
            return Optional.of("SMS campaigns");
        }
        if (whatsAppCampaignRepository.countByEventId(eventId) > 0) {
            return Optional.of("WhatsApp campaigns");
        }
        return Optional.empty();
    }
}
