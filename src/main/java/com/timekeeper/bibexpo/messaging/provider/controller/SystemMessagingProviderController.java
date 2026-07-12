package com.timekeeper.bibexpo.messaging.provider.controller;

import com.timekeeper.bibexpo.messaging.shared.enums.MessageChannel;
import com.timekeeper.bibexpo.messaging.provider.model.dto.request.SaveMessagingProviderRequest;
import com.timekeeper.bibexpo.messaging.provider.model.dto.response.MessagingProviderResponse;
import com.timekeeper.bibexpo.messaging.provider.service.MessagingProviderAdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Slf4j
public class SystemMessagingProviderController implements SystemMessagingProviderControllerApi {

    private final MessagingProviderAdminService providerAdminService;

    @Override
    public ResponseEntity<List<MessagingProviderResponse>> list() {
        return ResponseEntity.ok(providerAdminService.list());
    }

    @Override
    public ResponseEntity<MessagingProviderResponse> get(MessageChannel channel) {
        return ResponseEntity.ok(providerAdminService.get(channel));
    }

    @Override
    public ResponseEntity<MessagingProviderResponse> save(MessageChannel channel, SaveMessagingProviderRequest request) {
        log.info("Root saving {} provider configuration", channel);
        return ResponseEntity.ok(providerAdminService.save(channel, request));
    }
}
