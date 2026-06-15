package com.timekeeper.bibexpo.messaging.provider.service.impl;

import com.timekeeper.bibexpo.messaging.delivery.OutboundMessage;
import com.timekeeper.bibexpo.messaging.provider.model.entity.MessagingProvider;
import com.timekeeper.bibexpo.messaging.provider.service.MessagingProviderClient;
import com.timekeeper.bibexpo.messaging.shared.enums.MessageChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * No-network stand-in for {@link MessagingProviderClientImpl}, active when
 * {@code messaging.stub-enabled=true}. Logs what would be sent instead of calling the provider, so
 * dev and test never dispatch a real SMS/WhatsApp. Exactly one client bean is active at a time
 * (the real impl is the default via {@code matchIfMissing}).
 */
@Service
@ConditionalOnProperty(name = "messaging.stub-enabled", havingValue = "true")
@Slf4j
public class StubMessagingProviderClient implements MessagingProviderClient {

    @Override
    public void send(MessageChannel channel, OutboundMessage message) {
        log.info("[MSG-STUB] would send SYSTEM {} to {} — templateId={}, senderId={}, message='{}', variables={}",
                channel, message.getRecipientPhone(), message.getTemplateId(), message.getSenderId(),
                message.getMessage(), message.getVariables());
    }

    @Override
    public void send(MessagingProvider provider, OutboundMessage message) {
        log.info("[MSG-STUB] would send {} {} via [{} {}] to {} — templateId={}, senderId={}, message='{}', variables={}",
                provider.getUsage(), provider.getChannel(), provider.getHttpMethod(), provider.getBaseUrl(),
                message.getRecipientPhone(), message.getTemplateId(), message.getSenderId(),
                message.getMessage(), message.getVariables());
    }
}
