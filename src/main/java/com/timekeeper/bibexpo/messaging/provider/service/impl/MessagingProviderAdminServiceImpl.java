package com.timekeeper.bibexpo.messaging.provider.service.impl;

import com.timekeeper.bibexpo.messaging.shared.enums.MessageChannel;
import com.timekeeper.bibexpo.messaging.shared.exception.MessagingConfigNotFoundException;
import com.timekeeper.bibexpo.messaging.provider.model.dto.request.SaveMessagingProviderRequest;
import com.timekeeper.bibexpo.messaging.provider.model.dto.response.MessagingProviderResponse;
import com.timekeeper.bibexpo.messaging.provider.model.entity.MessagingProvider;
import com.timekeeper.bibexpo.messaging.provider.model.enums.MessageContentType;
import com.timekeeper.bibexpo.messaging.provider.repository.MessagingProviderRepository;
import com.timekeeper.bibexpo.messaging.provider.service.MessagingProviderAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MessagingProviderAdminServiceImpl implements MessagingProviderAdminService {

    private final MessagingProviderRepository providerRepository;

    @Override
    @Transactional(readOnly = true)
    public List<MessagingProviderResponse> list() {
        return providerRepository.findAll().stream()
                .sorted(Comparator.comparing(MessagingProvider::getChannel))
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public MessagingProviderResponse get(MessageChannel channel) {
        return toResponse(findOrThrow(channel));
    }

    @Override
    @Transactional
    public MessagingProviderResponse save(MessageChannel channel, SaveMessagingProviderRequest request) {
        MessagingProvider provider = providerRepository.findByChannel(channel)
                .orElseGet(() -> MessagingProvider.builder().channel(channel).build());

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

        return toResponse(providerRepository.save(provider));
    }

    private MessagingProvider findOrThrow(MessageChannel channel) {
        return providerRepository.findByChannel(channel)
                .orElseThrow(() -> new MessagingConfigNotFoundException(
                        "No provider is configured for the " + channel + " channel."));
    }

    private boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    private MessagingProviderResponse toResponse(MessagingProvider provider) {
        return MessagingProviderResponse.builder()
                .channel(provider.getChannel())
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
