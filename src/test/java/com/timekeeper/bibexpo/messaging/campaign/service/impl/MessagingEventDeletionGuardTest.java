package com.timekeeper.bibexpo.messaging.campaign.service.impl;

import com.timekeeper.bibexpo.messaging.campaign.repository.SmsCampaignRepository;
import com.timekeeper.bibexpo.messaging.campaign.repository.SmsTemplateRepository;
import com.timekeeper.bibexpo.messaging.campaign.repository.WhatsAppCampaignRepository;
import com.timekeeper.bibexpo.messaging.campaign.repository.WhatsAppTemplateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessagingEventDeletionGuardTest {

    private static final Long EVENT_ID = 1L;

    @Mock
    private SmsTemplateRepository smsTemplateRepository;
    @Mock
    private WhatsAppTemplateRepository whatsAppTemplateRepository;
    @Mock
    private SmsCampaignRepository smsCampaignRepository;
    @Mock
    private WhatsAppCampaignRepository whatsAppCampaignRepository;

    @InjectMocks
    private MessagingEventDeletionGuard guard;

    @Test
    void returnsEmptyWhenNoMessagingContentExists() {
        when(smsTemplateRepository.countByEventId(EVENT_ID)).thenReturn(0);
        when(whatsAppTemplateRepository.countByEventId(EVENT_ID)).thenReturn(0L);
        when(smsCampaignRepository.countByEventId(EVENT_ID)).thenReturn(0);
        when(whatsAppCampaignRepository.countByEventId(EVENT_ID)).thenReturn(0);

        assertThat(guard.findBlockingContent(EVENT_ID)).isEmpty();
    }

    @Test
    void reportsSmsTemplatesFirst() {
        when(smsTemplateRepository.countByEventId(EVENT_ID)).thenReturn(3);

        assertThat(guard.findBlockingContent(EVENT_ID)).contains("SMS templates");
    }

    @Test
    void reportsWhatsAppCampaignsWhenOnlyTheyRemain() {
        when(smsTemplateRepository.countByEventId(EVENT_ID)).thenReturn(0);
        when(whatsAppTemplateRepository.countByEventId(EVENT_ID)).thenReturn(0L);
        when(smsCampaignRepository.countByEventId(EVENT_ID)).thenReturn(0);
        when(whatsAppCampaignRepository.countByEventId(EVENT_ID)).thenReturn(2);

        Optional<String> blocking = guard.findBlockingContent(EVENT_ID);

        assertThat(blocking).contains("WhatsApp campaigns");
    }
}
