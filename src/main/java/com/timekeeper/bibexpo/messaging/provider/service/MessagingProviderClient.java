package com.timekeeper.bibexpo.messaging.provider.service;

import com.timekeeper.bibexpo.messaging.shared.enums.MessageChannel;
import com.timekeeper.bibexpo.messaging.delivery.OutboundMessage;

/**
 * Sends a system message over a channel using that channel's stored provider configuration.
 */
public interface MessagingProviderClient {

    /**
     * Builds and fires the provider call for the given channel.
     *
     * @param channel the channel whose provider row to use
     * @param message the recipient and rendered content
     * @throws com.timekeeper.bibexpo.messaging.provider.exception.MessagingProviderException
     *         if the provider is missing, disabled, or the send fails
     */
    void send(MessageChannel channel, OutboundMessage message);
}
