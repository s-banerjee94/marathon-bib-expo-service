package com.timekeeper.bibexpo.messaging.provider.controller;

import com.timekeeper.bibexpo.messaging.provider.model.dto.request.ProviderTestSendRequest;
import com.timekeeper.bibexpo.messaging.provider.model.dto.request.SaveMessagingProviderRequest;
import com.timekeeper.bibexpo.messaging.provider.model.dto.response.MessagingProviderResponse;
import com.timekeeper.bibexpo.messaging.provider.service.MessagingProviderAdminService;
import com.timekeeper.bibexpo.messaging.shared.enums.MessageChannel;
import com.timekeeper.bibexpo.model.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Slf4j
public class DefaultCampaignProviderController implements DefaultCampaignProviderControllerApi {

    private final MessagingProviderAdminService providerAdminService;

    @Override
    public ResponseEntity<List<MessagingProviderResponse>> list(User currentUser) {
        return ResponseEntity.ok(providerAdminService.listCampaignProviders(null, currentUser));
    }

    @Override
    public ResponseEntity<MessagingProviderResponse> get(MessageChannel channel, User currentUser) {
        return ResponseEntity.ok(providerAdminService.getCampaignProvider(channel, null, currentUser));
    }

    @Override
    public ResponseEntity<MessagingProviderResponse> save(MessageChannel channel,
                                                          SaveMessagingProviderRequest request, User currentUser) {
        log.info("Root saving default {} campaign provider", channel);
        return ResponseEntity.ok(providerAdminService.saveCampaignProvider(channel, null, request, currentUser));
    }

    @Override
    public ResponseEntity<Void> delete(MessageChannel channel, User currentUser) {
        log.info("Root deleting default {} campaign provider", channel);
        providerAdminService.deleteCampaignProvider(channel, null, currentUser);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<MessagingProviderResponse> testSend(MessageChannel channel,
                                                              ProviderTestSendRequest request, User currentUser) {
        log.info("Root test-sending default {} campaign provider", channel);
        return ResponseEntity.ok(providerAdminService.testSendCampaignProvider(channel, null, request, currentUser));
    }
}
