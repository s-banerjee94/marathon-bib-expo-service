package com.timekeeper.bibexpo.messaging.provider.service;

import com.timekeeper.bibexpo.messaging.shared.enums.MessageChannel;

/**
 * How many campaigns would stop working if a sender were switched off right now.
 *
 * <p>Declared here and implemented in the campaign slice so the provider slice can ask the question
 * without depending on campaign entities — the dependency between the two slices stays one-way.
 */
public interface ActiveCampaignCounter {

    /**
     * Campaigns currently armed or mid-dispatch on the channel.
     *
     * @param channel        channel the sender serves
     * @param organizationId organization whose campaigns to count, or null for the platform default,
     *                       which counts every organization's campaigns on the channel
     * @return the number of campaigns that would be affected
     */
    int countActive(MessageChannel channel, Long organizationId);
}
