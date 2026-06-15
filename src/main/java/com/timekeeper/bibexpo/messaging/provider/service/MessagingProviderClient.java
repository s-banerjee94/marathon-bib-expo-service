package com.timekeeper.bibexpo.messaging.provider.service;

import com.timekeeper.bibexpo.messaging.shared.enums.MessageChannel;
import com.timekeeper.bibexpo.messaging.delivery.OutboundMessage;
import com.timekeeper.bibexpo.messaging.provider.model.entity.MessagingProvider;

/**
 * Builds and fires a provider's HTTP call from its stored configuration. System messages resolve
 * the platform-default row by channel; campaigns pass an already-resolved provider (the org override
 * or platform default chosen by {@link CampaignProviderResolver}).
 */
public interface MessagingProviderClient {

    /**
     * Sends over the platform-default SYSTEM provider for the channel.
     *
     * @param channel the channel whose SYSTEM provider row to use
     * @param message the recipient and rendered content
     * @throws com.timekeeper.bibexpo.messaging.provider.exception.MessagingProviderException
     *         if the provider is missing, disabled, or the send fails
     */
    void send(MessageChannel channel, OutboundMessage message);

    /**
     * Sends through a specific, already-resolved provider row.
     *
     * @param provider the provider configuration to replay into an HTTP call
     * @param message  the recipient and rendered content
     * @throws com.timekeeper.bibexpo.messaging.provider.exception.MessagingProviderException
     *         if the provider is disabled, misconfigured, or the send fails
     */
    void send(MessagingProvider provider, OutboundMessage message);
}
