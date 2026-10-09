package com.timekeeper.bibexpo.messaging.provider.service;

import com.timekeeper.bibexpo.messaging.provider.model.dto.request.ProviderTestSendRequest;
import com.timekeeper.bibexpo.messaging.provider.model.dto.request.SaveMessagingProviderRequest;
import com.timekeeper.bibexpo.messaging.provider.model.dto.response.MessagingProviderResponse;
import com.timekeeper.bibexpo.messaging.shared.enums.MessageChannel;
import com.timekeeper.bibexpo.user.model.entity.User;

import java.util.List;

/**
 * Management of provider configurations. The system (transactional) methods are root-only; the
 * campaign methods serve both the platform default (organizationId null, root-only) and an
 * organization's own override (organizationId set, that organization or root/admin).
 */
public interface MessagingProviderAdminService {

    /** Every SYSTEM provider row, ordered by channel, secrets masked. */
    List<MessagingProviderResponse> list();

    /**
     * @throws com.timekeeper.bibexpo.messaging.shared.exception.MessagingConfigNotFoundException
     *         if no SYSTEM provider row exists for the channel
     */
    MessagingProviderResponse get(MessageChannel channel);

    /**
     * Creates the channel's SYSTEM provider row or replaces its fields. Blank secret fields keep the
     * stored value.
     */
    MessagingProviderResponse save(MessageChannel channel, SaveMessagingProviderRequest request);

    /** Campaign providers for the scope: platform defaults when organizationId is null, else the org's overrides. */
    List<MessagingProviderResponse> listCampaignProviders(Long organizationId, User currentUser);

    /**
     * @throws com.timekeeper.bibexpo.messaging.shared.exception.MessagingConfigNotFoundException
     *         if the scope has no campaign provider for the channel
     */
    MessagingProviderResponse getCampaignProvider(MessageChannel channel, Long organizationId, User currentUser);

    /**
     * Creates the scope's campaign provider for the channel or replaces its fields. Blank secret fields keep
     * the stored value. Switching off an enabled sender is refused while campaigns are armed on it, unless
     * {@code force} is set on the platform default.
     *
     * @throws com.timekeeper.bibexpo.messaging.provider.exception.MessagingProviderInUseException
     *         if armed campaigns depend on the sender being switched off
     */
    MessagingProviderResponse saveCampaignProvider(MessageChannel channel, Long organizationId, boolean force,
                                                   SaveMessagingProviderRequest request, User currentUser);

    /**
     * Removes the scope's campaign provider for the channel, under the same armed-campaign rule as switching
     * it off; campaigns then fall back to the platform default.
     *
     * @throws com.timekeeper.bibexpo.messaging.shared.exception.MessagingConfigNotFoundException
     *         if the scope has no campaign provider for the channel
     * @throws com.timekeeper.bibexpo.messaging.provider.exception.MessagingProviderInUseException
     *         if armed campaigns depend on the sender
     */
    void deleteCampaignProvider(MessageChannel channel, Long organizationId, boolean force, User currentUser);

    /** Fires one real message through the stored provider to verify it; throws if the send fails. */
    MessagingProviderResponse testSendCampaignProvider(MessageChannel channel, Long organizationId,
                                                       ProviderTestSendRequest request, User currentUser);
}
