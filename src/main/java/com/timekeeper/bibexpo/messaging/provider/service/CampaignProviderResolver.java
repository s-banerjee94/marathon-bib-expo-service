package com.timekeeper.bibexpo.messaging.provider.service;

import com.timekeeper.bibexpo.messaging.provider.exception.MessagingProviderException;
import com.timekeeper.bibexpo.messaging.provider.model.entity.MessagingProvider;
import com.timekeeper.bibexpo.messaging.shared.enums.MessageChannel;
import com.timekeeper.bibexpo.messaging.shared.enums.MessageUsage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * The single place the override-vs-default decision lives for campaigns: an organization's own
 * enabled provider for the channel if it has one, otherwise the platform-default provider. Mirrors
 * the SYSTEM lookup that {@code MessagingProviderClient} uses for transactional messages.
 *
 * <p>A disabled organization override falls through to the platform default, matching the old
 * DEFAULT/CUSTOM behaviour: switching off a custom sender reverts to the shared one.
 */
@Component
@RequiredArgsConstructor
public class CampaignProviderResolver {

    private final MessagingProviderCache providerCache;

    // When true (dev/test), an unconfigured channel resolves to a placeholder so the stub client
    // still renders the would-be request to the console instead of failing the campaign.
    @Value("${messaging.stub-enabled:false}")
    private boolean stubEnabled;

    /**
     * Resolves the campaign provider for an organization on the given channel.
     *
     * @param channel        the delivery channel
     * @param organizationId the sending organization, or null to use the platform default directly
     * @return the organization's enabled override, or the enabled platform default
     * @throws MessagingProviderException if neither an enabled override nor an enabled default exists
     *                                    (unless the stub is enabled, which yields a placeholder)
     */
    public MessagingProvider resolve(MessageChannel channel, Long organizationId) {
        return organizationOverride(channel, organizationId)
                .or(() -> platformDefault(channel))
                .orElseGet(() -> fallbackOrThrow(channel, organizationId));
    }

    private MessagingProvider fallbackOrThrow(MessageChannel channel, Long organizationId) {
        if (stubEnabled) {
            return MessagingProvider.builder()
                    .channel(channel)
                    .usage(MessageUsage.CAMPAIGN)
                    .organizationId(organizationId)
                    .baseUrl("stub://" + channel.name().toLowerCase() + "-campaign")
                    .enabled(true)
                    .build();
        }
        throw new MessagingProviderException(
                "No " + channel + " campaign provider is configured.");
    }

    private Optional<MessagingProvider> organizationOverride(MessageChannel channel, Long organizationId) {
        if (organizationId == null) {
            return Optional.empty();
        }
        return providerCache.findCampaignOverride(channel, organizationId)
                .filter(MessagingProvider::isEnabled);
    }

    private Optional<MessagingProvider> platformDefault(MessageChannel channel) {
        return providerCache.findCampaignDefault(channel)
                .filter(MessagingProvider::isEnabled);
    }
}
