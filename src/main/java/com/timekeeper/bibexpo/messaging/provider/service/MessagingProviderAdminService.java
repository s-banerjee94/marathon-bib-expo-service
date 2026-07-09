package com.timekeeper.bibexpo.messaging.provider.service;

import com.timekeeper.bibexpo.messaging.shared.enums.MessageChannel;
import com.timekeeper.bibexpo.messaging.provider.model.dto.request.ProviderTestSendRequest;
import com.timekeeper.bibexpo.messaging.provider.model.dto.request.SaveMessagingProviderRequest;
import com.timekeeper.bibexpo.messaging.provider.model.dto.response.MessagingProviderResponse;
import com.timekeeper.bibexpo.model.entity.User;

import java.util.List;

/**
 * Management of provider configurations. The system (transactional) methods are root-only; the
 * campaign methods serve both the platform default (organizationId null, root-only) and an
 * organization's own override (organizationId set, that organization or root/admin).
 */
public interface MessagingProviderAdminService {

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

    MessagingProviderResponse getCampaignProvider(MessageChannel channel, Long organizationId, User currentUser);

    MessagingProviderResponse saveCampaignProvider(MessageChannel channel, Long organizationId,
                                                   SaveMessagingProviderRequest request, User currentUser);

    void deleteCampaignProvider(MessageChannel channel, Long organizationId, User currentUser);

    /** Fires one real message through the stored provider to verify it; throws if the send fails. */
    MessagingProviderResponse testSendCampaignProvider(MessageChannel channel, Long organizationId,
                                                       ProviderTestSendRequest request, User currentUser);
}
