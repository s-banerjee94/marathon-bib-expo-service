package com.timekeeper.bibexpo.messaging.provider.controller;

import com.timekeeper.bibexpo.messaging.provider.model.dto.request.ProviderTestSendRequest;
import com.timekeeper.bibexpo.messaging.provider.model.dto.request.SaveMessagingProviderRequest;
import com.timekeeper.bibexpo.messaging.provider.model.dto.response.MessagingProviderResponse;
import com.timekeeper.bibexpo.messaging.provider.service.MessagingProviderAdminService;
import com.timekeeper.bibexpo.messaging.shared.enums.MessageChannel;
import com.timekeeper.bibexpo.user.model.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
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
    public ResponseEntity<MessagingProviderResponse> save(MessageChannel channel, boolean force,
                                                          SaveMessagingProviderRequest request, User currentUser) {
        return ResponseEntity.ok(providerAdminService.saveCampaignProvider(channel, null, force, request, currentUser));
    }

    @Override
    public ResponseEntity<Void> delete(MessageChannel channel, boolean force, User currentUser) {
        providerAdminService.deleteCampaignProvider(channel, null, force, currentUser);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<MessagingProviderResponse> testSend(MessageChannel channel,
                                                              ProviderTestSendRequest request, User currentUser) {
        return ResponseEntity.ok(providerAdminService.testSendCampaignProvider(channel, null, request, currentUser));
    }
}
