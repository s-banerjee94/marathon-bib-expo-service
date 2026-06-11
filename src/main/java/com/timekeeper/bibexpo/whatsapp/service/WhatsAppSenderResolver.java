package com.timekeeper.bibexpo.whatsapp.service;

import com.timekeeper.bibexpo.whatsapp.config.WhatsAppApiProperties;
import com.timekeeper.bibexpo.whatsapp.model.WhatsAppSender;
import com.timekeeper.bibexpo.whatsapp.model.enums.WhatsAppSenderMode;
import com.timekeeper.bibexpo.whatsapp.model.enums.WhatsAppSenderScope;
import com.timekeeper.bibexpo.whatsapp.repository.OrganizationWhatsAppConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The single place the default-vs-custom sender decision lives. Every organization send
 * path resolves through here; platform-initiated messages (bill PDF, OTP) bypass it and
 * use {@link #defaultSender()} directly.
 */
@Component
@RequiredArgsConstructor
public class WhatsAppSenderResolver {

    private final OrganizationWhatsAppConfigRepository configRepository;
    private final WhatsAppApiProperties properties;

    public WhatsAppSender resolve(Long organizationId) {
        return configRepository.findByOrganizationId(organizationId)
                .filter(config -> config.getSenderMode() == WhatsAppSenderMode.CUSTOM)
                .map(config -> WhatsAppSender.builder()
                        .accountSid(config.getAccountSid())
                        .authToken(config.getAuthToken())
                        .fromNumber(config.getFromNumber())
                        .scope(WhatsAppSenderScope.ORGANIZATION)
                        .build())
                .orElseGet(this::defaultSender);
    }

    public WhatsAppSender defaultSender() {
        return WhatsAppSender.builder()
                .accountSid(properties.getAccountSid())
                .authToken(properties.getAuthToken())
                .fromNumber(properties.getFromNumber())
                .scope(WhatsAppSenderScope.DEFAULT)
                .build();
    }
}
