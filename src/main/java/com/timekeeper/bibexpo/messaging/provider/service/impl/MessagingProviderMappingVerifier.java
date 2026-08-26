package com.timekeeper.bibexpo.messaging.provider.service.impl;

import com.timekeeper.bibexpo.messaging.provider.model.entity.MessagingProvider;
import com.timekeeper.bibexpo.messaging.provider.repository.MessagingProviderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Reports stored provider rows whose declared mode contradicts their own request mapping — the rows
 * that render a message and then drop it, sending the vendor empty variables.
 *
 * <p>An enabled row cannot reach this state any more: {@link ProviderMappingValidator} rejects it on
 * save, and enabling a row runs that same check. Only rows written before that gate can still be
 * both enabled and inconsistent, which is what this pass is looking for. Disabled rows are skipped
 * on purpose — saving a broken row disabled is how a broken sender gets switched off, so reporting
 * one would be a false alarm at ERROR level, and those are how a team learns to stop reading startup
 * logs.
 *
 * <p>It only warns. Unlike the system-template contract, which is code an operator cannot fix at
 * runtime, these are rows an organizer creates, so one bad row must not keep the platform from
 * starting. Fix the row and the warning goes away.
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
        long inconsistent = providers.stream()
                .filter(provider -> mappingValidator.inconsistency(provider)
                        .map(problem -> {
                            log.error("Messaging provider {} {} (organization {}) is misconfigured: {}",
                                    provider.getUsage(), provider.getChannel(),
                                    provider.getOrganizationId() == null ? "platform default" : provider.getOrganizationId(),
                                    problem);
                            return true;
                        })
                        .orElse(false))
                .count();

        reportInconsistent(providers, inconsistent);
    }

    private void reportInconsistent(List<MessagingProvider> providers, long inconsistent) {
        if (inconsistent == 0) {
            log.info("Verified request mappings for {} enabled messaging provider(s)", providers.size());
        } else {
            log.error("{} of {} enabled messaging provider(s) declare a rendering their request does not match — "
                    + "messages sent through them lose their content", inconsistent, providers.size());
        }
    }
}
