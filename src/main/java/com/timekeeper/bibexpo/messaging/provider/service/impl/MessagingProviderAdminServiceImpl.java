package com.timekeeper.bibexpo.messaging.provider.service.impl;

import com.timekeeper.bibexpo.exception.OrganizationNotFoundException;
import com.timekeeper.bibexpo.exception.AccessForbiddenException;
import com.timekeeper.bibexpo.messaging.delivery.OutboundMessage;
import com.timekeeper.bibexpo.messaging.provider.model.dto.request.ProviderTestSendRequest;
import com.timekeeper.bibexpo.messaging.provider.model.dto.request.SaveMessagingProviderRequest;
import com.timekeeper.bibexpo.messaging.provider.model.dto.response.MessagingProviderResponse;
import com.timekeeper.bibexpo.messaging.provider.model.entity.MessagingProvider;
import com.timekeeper.bibexpo.messaging.provider.model.enums.MessageContentType;
import com.timekeeper.bibexpo.messaging.provider.repository.MessagingProviderRepository;
import com.timekeeper.bibexpo.messaging.provider.service.MessagingProviderAdminService;
import com.timekeeper.bibexpo.messaging.provider.service.MessagingProviderCache;
import com.timekeeper.bibexpo.messaging.provider.service.MessagingProviderClient;
import com.timekeeper.bibexpo.messaging.shared.enums.MessageChannel;
import com.timekeeper.bibexpo.messaging.shared.enums.MessageUsage;
import com.timekeeper.bibexpo.messaging.shared.exception.MessagingConfigNotFoundException;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.entity.UserRole;
import com.timekeeper.bibexpo.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MessagingProviderAdminServiceImpl implements MessagingProviderAdminService {

    private final MessagingProviderRepository providerRepository;
    private final OrganizationRepository organizationRepository;
    private final MessagingProviderClient messagingProviderClient;
    private final MessagingProviderCache providerCache;

    // ---- SYSTEM (root) ----

    @Override
    @Transactional(readOnly = true)
    public List<MessagingProviderResponse> list() {
        return providerRepository.findByUsage(MessageUsage.SYSTEM).stream()
                .sorted(Comparator.comparing(MessagingProvider::getChannel))
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public MessagingProviderResponse get(MessageChannel channel) {
        return toResponse(findOrThrow(MessageUsage.SYSTEM, channel, null));
    }

    @Override
    @Transactional
    public MessagingProviderResponse save(MessageChannel channel, SaveMessagingProviderRequest request) {
        MessagingProvider saved = upsert(MessageUsage.SYSTEM, channel, null, request);
        providerCache.evictSystem(channel);
        return toResponse(saved);
    }

    // ---- CAMPAIGN (platform default when organizationId == null, org override otherwise) ----

    @Override
    @Transactional(readOnly = true)
    public List<MessagingProviderResponse> listCampaignProviders(Long organizationId, User currentUser) {
        authorize(currentUser, organizationId);
        verifyOrganizationExists(organizationId);

        List<MessagingProvider> rows = organizationId == null
                ? providerRepository.findByUsageAndOrganizationIdIsNull(MessageUsage.CAMPAIGN)
                : providerRepository.findByUsageAndOrganizationId(MessageUsage.CAMPAIGN, organizationId);

        return rows.stream()
                .sorted(Comparator.comparing(MessagingProvider::getChannel))
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public MessagingProviderResponse getCampaignProvider(MessageChannel channel, Long organizationId, User currentUser) {
        authorize(currentUser, organizationId);
        verifyOrganizationExists(organizationId);
        return toResponse(findOrThrow(MessageUsage.CAMPAIGN, channel, organizationId));
    }

    @Override
    @Transactional
    public MessagingProviderResponse saveCampaignProvider(MessageChannel channel, Long organizationId,
                                                          SaveMessagingProviderRequest request, User currentUser) {
        authorize(currentUser, organizationId);
        verifyOrganizationExists(organizationId);
        MessagingProvider saved = upsert(MessageUsage.CAMPAIGN, channel, organizationId, request);
        evictCampaign(channel, organizationId);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public void deleteCampaignProvider(MessageChannel channel, Long organizationId, User currentUser) {
        authorize(currentUser, organizationId);
        verifyOrganizationExists(organizationId);
        providerRepository.delete(findOrThrow(MessageUsage.CAMPAIGN, channel, organizationId));
        evictCampaign(channel, organizationId);
    }

    // Intentionally non-transactional: the real gateway call must not run inside a DB transaction.
    @Override
    public MessagingProviderResponse testSendCampaignProvider(MessageChannel channel, Long organizationId,
                                                              ProviderTestSendRequest request, User currentUser) {
        authorize(currentUser, organizationId);
        verifyOrganizationExists(organizationId);

        MessagingProvider provider = findOrThrow(MessageUsage.CAMPAIGN, channel, organizationId);
        messagingProviderClient.send(provider, OutboundMessage.builder()
                .recipientPhone(request.getRecipientPhone())
                .templateId(request.getTemplateId())
                .senderId(request.getSenderId())
                .message(request.getMessage())
                .variables(request.getVariables())
                .build());
        return toResponse(provider);
    }

    // ---- shared core ----

    // Upsert by the (usage, channel, organizationId) key, so a platform default is never duplicated
    // despite MySQL treating NULL organization ids as distinct in the unique index.
    private MessagingProvider upsert(MessageUsage usage, MessageChannel channel, Long organizationId,
                                     SaveMessagingProviderRequest request) {
        MessagingProvider provider = findExisting(usage, channel, organizationId)
                .orElseGet(() -> MessagingProvider.builder()
                        .channel(channel).usage(usage).organizationId(organizationId).build());

        provider.setBaseUrl(request.getBaseUrl());
        provider.setHttpMethod(request.getHttpMethod());
        provider.setAuthType(request.getAuthType());
        provider.setUsername(request.getUsername());
        provider.setTemplateMode(request.getTemplateMode());
        provider.setContentType(request.getContentType() == null ? MessageContentType.JSON : request.getContentType());
        provider.setRequestParams(request.getRequestParams() == null ? List.of() : request.getRequestParams());
        provider.setBodyTemplate(request.getBodyTemplate());
        provider.setEnabled(request.isEnabled());

        // Secrets are write-only: a blank value on update keeps the stored credential.
        if (isPresent(request.getAuthToken())) {
            provider.setAuthToken(request.getAuthToken());
        }
        if (isPresent(request.getPassword())) {
            provider.setPassword(request.getPassword());
        }

        return providerRepository.save(provider);
    }

    private void evictCampaign(MessageChannel channel, Long organizationId) {
        if (organizationId == null) {
            providerCache.evictCampaignDefault(channel);
        } else {
            providerCache.evictCampaignOverride(channel, organizationId);
        }
    }

    private MessagingProvider findOrThrow(MessageUsage usage, MessageChannel channel, Long organizationId) {
        return findExisting(usage, channel, organizationId)
                .orElseThrow(() -> new MessagingConfigNotFoundException(
                        "No provider is configured for the " + channel + " channel."));
    }

    private Optional<MessagingProvider> findExisting(MessageUsage usage, MessageChannel channel, Long organizationId) {
        return organizationId == null
                ? providerRepository.findByUsageAndChannelAndOrganizationIdIsNull(usage, channel)
                : providerRepository.findByUsageAndChannelAndOrganizationId(usage, channel, organizationId);
    }

    private void authorize(User user, Long organizationId) {
        UserRole role = user.getRole();
        if (role == UserRole.ROOT) {
            return;
        }
        if (organizationId == null) {
            throw new AccessForbiddenException("Only the root user can manage the default campaign providers.");
        }
        if (role == UserRole.ADMIN) {
            return;
        }
        if (user.getOrganization() == null || !user.getOrganization().getId().equals(organizationId)) {
            throw new AccessForbiddenException("You do not have access to this organization's campaign settings.");
        }
    }

    private void verifyOrganizationExists(Long organizationId) {
        if (organizationId == null) {
            return;
        }
        organizationRepository.findById(organizationId)
                .orElseThrow(OrganizationNotFoundException::new);
    }

    private boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    private MessagingProviderResponse toResponse(MessagingProvider provider) {
        return MessagingProviderResponse.builder()
                .channel(provider.getChannel())
                .usage(provider.getUsage())
                .organizationId(provider.getOrganizationId())
                .baseUrl(provider.getBaseUrl())
                .httpMethod(provider.getHttpMethod())
                .authType(provider.getAuthType())
                .authTokenMasked(mask(provider.getAuthToken()))
                .username(provider.getUsername())
                .passwordMasked(mask(provider.getPassword()))
                .templateMode(provider.getTemplateMode())
                .contentType(provider.getContentType())
                .requestParams(provider.getRequestParams())
                .bodyTemplate(provider.getBodyTemplate())
                .enabled(provider.isEnabled())
                .createdAt(provider.getCreatedAt())
                .updatedAt(provider.getUpdatedAt())
                .build();
    }

    /** Shows only a short tail of a secret, or null when unset, so the full value is never returned. */
    private String mask(String secret) {
        if (secret == null || secret.isBlank()) {
            return null;
        }
        return secret.length() < 4 ? "••••" : "••••" + secret.substring(secret.length() - 4);
    }
}
