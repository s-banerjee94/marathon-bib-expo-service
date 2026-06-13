package com.timekeeper.bibexpo.messaging.provider.service;

import com.timekeeper.bibexpo.messaging.shared.enums.MessageChannel;
import com.timekeeper.bibexpo.messaging.provider.model.dto.request.SaveMessagingProviderRequest;
import com.timekeeper.bibexpo.messaging.provider.model.dto.response.MessagingProviderResponse;

import java.util.List;

/**
 * Root management of the per-channel provider configurations: list, read, and upsert by channel.
 */
public interface MessagingProviderAdminService {

    List<MessagingProviderResponse> list();

    /**
     * @throws com.timekeeper.bibexpo.messaging.shared.exception.MessagingConfigNotFoundException
     *         if no provider row exists for the channel
     */
    MessagingProviderResponse get(MessageChannel channel);

    /**
     * Creates the channel's provider row or replaces its fields. Blank secret fields keep the
     * stored value.
     */
    MessagingProviderResponse save(MessageChannel channel, SaveMessagingProviderRequest request);
}
