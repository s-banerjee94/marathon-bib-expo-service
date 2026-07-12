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
public class OrganizationCampaignProviderController implements OrganizationCampaignProviderControllerApi {

    private final MessagingProviderAdminService providerAdminService;

    @Override
    public ResponseEntity<List<MessagingProviderResponse>> list(Long organizationId, User currentUser) {
        return ResponseEntity.ok(providerAdminService.listCampaignProviders(organizationId, currentUser));
    }

    @Override
    public ResponseEntity<MessagingProviderResponse> get(Long organizationId, MessageChannel channel, User currentUser) {
        return ResponseEntity.ok(providerAdminService.getCampaignProvider(channel, organizationId, currentUser));
    }

    @Override
    public ResponseEntity<MessagingProviderResponse> save(Long organizationId, MessageChannel channel,
                                                          SaveMessagingProviderRequest request, User currentUser) {
        log.info("Saving {} campaign provider for organization ID: {} by user: {}",
                channel, organizationId, currentUser.getUsername());
        return ResponseEntity.ok(providerAdminService.saveCampaignProvider(channel, organizationId, request, currentUser));
    }

    @Override
    public ResponseEntity<Void> delete(Long organizationId, MessageChannel channel, User currentUser) {
        log.info("Deleting {} campaign provider for organization ID: {} by user: {}",
                channel, organizationId, currentUser.getUsername());
        providerAdminService.deleteCampaignProvider(channel, organizationId, currentUser);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<MessagingProviderResponse> testSend(Long organizationId, MessageChannel channel,
                                                              ProviderTestSendRequest request, User currentUser) {
        log.info("Test-sending {} campaign provider for organization ID: {} by user: {}",
                channel, organizationId, currentUser.getUsername());
        return ResponseEntity.ok(providerAdminService.testSendCampaignProvider(channel, organizationId, request, currentUser));
    }
}
