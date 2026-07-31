package com.timekeeper.bibexpo.config;

import com.timekeeper.bibexpo.messaging.provider.model.entity.MessagingProvider;
import com.timekeeper.bibexpo.messaging.provider.repository.MessagingProviderRepository;
import com.timekeeper.bibexpo.messaging.provider.service.impl.ProviderMappingValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Reports stored provider rows whose declared mode contradicts their own request mapping — the rows
 * that render a message and then drop it, sending the vendor empty variables.
 *
 * <p>New and edited rows are rejected outright by {@link ProviderMappingValidator} on save. This pass
 * exists for rows written before that gate, and only warns: unlike the system-template contract,
 * which is code an operator cannot fix at runtime, these are rows an organizer creates, so one bad
 * row must not keep the platform from starting. Fix the row and the warning goes away.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class MessagingProviderMappingVerifier implements CommandLineRunner {

    private final MessagingProviderRepository providerRepository;
    private final ProviderMappingValidator mappingValidator;

    // The country code used to be hardcoded in the sender, so rows written before it became a field
    // carry none. Restoring it keeps those senders addressing numbers exactly as they did before,
    // instead of silently dropping the code off every recipient.
    private static final String LEGACY_COUNTRY_CODE = "91";

    @Override
    public void run(String... args) {
        List<MessagingProvider> providers = providerRepository.findAll();
        backfillCountryCode(providers);
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

    private void backfillCountryCode(List<MessagingProvider> providers) {
        List<MessagingProvider> missing = providers.stream()
                .filter(provider -> provider.getDefaultCountryCode() == null
                        || provider.getDefaultCountryCode().isBlank())
                .toList();
        if (missing.isEmpty()) {
            return;
        }
        missing.forEach(provider -> provider.setDefaultCountryCode(LEGACY_COUNTRY_CODE));
        providerRepository.saveAll(missing);
        log.warn("Set the country code to {} on {} messaging provider(s) that predate the setting — check it is right "
                + "for each of them", LEGACY_COUNTRY_CODE, missing.size());
    }

    private void reportInconsistent(List<MessagingProvider> providers, long inconsistent) {
        if (inconsistent == 0) {
            log.info("Verified request mappings for {} messaging provider(s)", providers.size());
        } else {
            log.error("{} of {} messaging provider(s) declare a rendering their request does not match — "
                    + "messages sent through them lose their content", inconsistent, providers.size());
        }
    }
}
