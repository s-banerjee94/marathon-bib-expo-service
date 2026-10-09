package com.timekeeper.bibexpo.messaging.provider.service.impl;

import com.timekeeper.bibexpo.messaging.provider.model.entity.MessagingProvider;
import com.timekeeper.bibexpo.messaging.provider.repository.MessagingProviderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Reports enabled provider rows that fail {@link ProviderMappingValidator}, which only rows saved before that
 * check existed can still do. It logs rather than fails startup: these are rows an organizer can fix, and one
 * bad row must not keep the platform down. Disabled rows are skipped, since switching a broken sender off is
 * how it gets fixed.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MessagingProviderMappingVerifier implements CommandLineRunner {

    private final MessagingProviderRepository providerRepository;
    private final ProviderMappingValidator mappingValidator;

    @Override
    public void run(String... args) {
        List<MessagingProvider> providers = providerRepository.findAll().stream()
                .filter(MessagingProvider::isEnabled)
                .toList();
        int misconfigured = 0;
        for (MessagingProvider provider : providers) {
            Optional<String> problem = mappingValidator.inconsistency(provider);
            if (problem.isPresent()) {
                misconfigured++;
                log.atError()
                        .addKeyValue("event", "message.provider_misconfigured")
                        .addKeyValue("usage", provider.getUsage())
                        .addKeyValue("channel", provider.getChannel())
                        .addKeyValue("organizationId", provider.getOrganizationId())
                        .addKeyValue("reason", problem.get())
                        .log("Messaging provider is misconfigured and loses message content");
            }
        }
        log.atInfo()
                .addKeyValue("event", "message.providers_verified")
                .addKeyValue("enabled", providers.size())
                .addKeyValue("misconfigured", misconfigured)
                .log("Verified messaging provider request mappings");
    }
}
