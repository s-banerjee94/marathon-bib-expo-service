package com.timekeeper.bibexpo.messaging.provider.service.impl;

import com.timekeeper.bibexpo.messaging.delivery.OutboundMessage;
import com.timekeeper.bibexpo.messaging.provider.model.entity.MessagingProvider;
import com.timekeeper.bibexpo.messaging.provider.service.MessagingProviderCache;
import com.timekeeper.bibexpo.messaging.provider.service.MessagingProviderClient;
import com.timekeeper.bibexpo.messaging.provider.service.impl.ProviderRequestBuilder.ProviderRequest;
import com.timekeeper.bibexpo.messaging.shared.enums.MessageChannel;
import com.timekeeper.bibexpo.messaging.shared.enums.MessageUsage;
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
 * <p>The would-be request is logged at DEBUG, as the provider's mapping actually produces it — so a field
 * the mapping never references does not appear, and what is logged is what production would put on the
 * wire. Only when the row carries no mapping at all, such as the placeholder the campaign resolver returns
 * when no provider is configured, does it fall back to summarising the outbound message. It carries the
 * recipient's number and the message, so INFO only records that a send was stubbed.
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
        logStubbed(MessageUsage.SYSTEM, channel, null);
        if (provider == null) {
            log.debug("Would send SYSTEM {} to {}, no provider configured; {}",
                    channel, message.getRecipientPhone(), summarise(message));
            return;
        }
        logRequest(provider, message);
    }

    @Override
    public void send(MessagingProvider provider, OutboundMessage message) {
        logStubbed(provider.getUsage(), provider.getChannel(), provider.getOrganizationId());
        logRequest(provider, message);
    }

    private void logStubbed(MessageUsage usage, MessageChannel channel, Long organizationId) {
        log.atInfo()
                .addKeyValue("event", "message.stubbed")
                .addKeyValue("channel", channel)
                .addKeyValue("usage", usage)
                .addKeyValue("organizationId", organizationId)
                .log("Message not sent, the messaging stub is on");
    }

    private void logRequest(MessagingProvider provider, OutboundMessage message) {
        if (!log.isDebugEnabled()) {
            return;
        }
        if (!requestBuilder.carriesMessage(provider)) {
            log.debug("Would send {} {} via [{}] to {}, provider has no request mapping; {}",
                    provider.getUsage(), provider.getChannel(), provider.getBaseUrl(),
                    message.getRecipientPhone(), summarise(message));
            return;
        }
        ProviderRequest request = requestBuilder.redacted(requestBuilder.build(provider, message), provider);
        log.debug("Would send {} {} to {}: {} {} headers={} body={}", provider.getUsage(), provider.getChannel(),
                message.getRecipientPhone(), request.method(), request.url(), request.headers(), request.body());
    }

    private String summarise(OutboundMessage message) {
        return String.format("templateId=%s, senderId=%s, message='%s', variables=%s",
                message.getTemplateId(), message.getSenderId(), message.getMessage(), message.getVariables());
    }
}
