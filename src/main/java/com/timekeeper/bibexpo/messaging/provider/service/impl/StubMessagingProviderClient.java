package com.timekeeper.bibexpo.messaging.provider.service.impl;

import com.timekeeper.bibexpo.messaging.delivery.OutboundMessage;
import com.timekeeper.bibexpo.messaging.provider.model.entity.MessagingProvider;
import com.timekeeper.bibexpo.messaging.provider.service.MessagingProviderCache;
import com.timekeeper.bibexpo.messaging.provider.service.MessagingProviderClient;
import com.timekeeper.bibexpo.messaging.provider.service.impl.ProviderRequestBuilder.ProviderRequest;
import com.timekeeper.bibexpo.messaging.shared.enums.MessageChannel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * No-network stand-in for {@link MessagingProviderClientImpl}, active when
 * {@code messaging.stub-enabled=true}. Logs what would be sent instead of calling the provider, so
 * dev and test never dispatch a real SMS/WhatsApp. Exactly one client bean is active at a time
 * (the real impl is the default via {@code matchIfMissing}).
 *
 * <p>The line shows the request the provider's mapping actually produces — so a field the mapping
 * never references (a leftover message body under a provider-rendered provider, for instance) does
 * not appear, and what is logged is what production would put on the wire. Only when the row carries
 * no mapping at all, such as the placeholder the campaign resolver returns when no provider is
 * configured, does it fall back to summarising the outbound message.
 */
@Service
@ConditionalOnProperty(name = "messaging.stub-enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class StubMessagingProviderClient implements MessagingProviderClient {

    private final MessagingProviderCache providerCache;
    private final ProviderRequestBuilder requestBuilder;

    @Override
    public void send(MessageChannel channel, OutboundMessage message) {
        MessagingProvider provider = providerCache.findSystem(channel).orElse(null);
        if (provider == null) {
            log.info("[MSG-STUB] would send SYSTEM {} to {} — no provider configured; {}",
                    channel, message.getRecipientPhone(), summarise(message));
            return;
        }
        logRequest("SYSTEM " + channel, provider, message);
    }

    @Override
    public void send(MessagingProvider provider, OutboundMessage message) {
        logRequest(provider.getUsage() + " " + provider.getChannel(), provider, message);
    }

    private void logRequest(String label, MessagingProvider provider, OutboundMessage message) {
        if (!requestBuilder.carriesMessage(provider)) {
            log.info("[MSG-STUB] would send {} via [{}] to {} — provider has no request mapping; {}",
                    label, provider.getBaseUrl(), message.getRecipientPhone(), summarise(message));
            return;
        }

        ProviderRequest request = requestBuilder.redacted(requestBuilder.build(provider, message), provider);
        log.info("[MSG-STUB] would send {} to {} — {} {}",
                label, message.getRecipientPhone(), request.method(), request.url());
        if (!request.headers().isEmpty()) {
            log.info("[MSG-STUB] headers: {}", request.headers());
        }
        if (request.body() != null && !request.body().isBlank()) {
            log.info("[MSG-STUB] body: {}", request.body());
        }
    }

    private String summarise(OutboundMessage message) {
        return String.format("templateId=%s, senderId=%s, message='%s', variables=%s",
                message.getTemplateId(), message.getSenderId(), message.getMessage(), message.getVariables());
    }
}
