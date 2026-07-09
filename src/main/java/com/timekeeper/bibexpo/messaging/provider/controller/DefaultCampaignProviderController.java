package com.timekeeper.bibexpo.messaging.provider.controller;

import com.timekeeper.bibexpo.exception.ErrorResponse;
import com.timekeeper.bibexpo.messaging.provider.exception.MessagingProviderException;
import com.timekeeper.bibexpo.messaging.provider.model.dto.request.ProviderTestSendRequest;
import com.timekeeper.bibexpo.messaging.provider.model.dto.request.SaveMessagingProviderRequest;
import com.timekeeper.bibexpo.messaging.provider.model.dto.response.MessagingProviderResponse;
import com.timekeeper.bibexpo.messaging.provider.service.MessagingProviderAdminService;
import com.timekeeper.bibexpo.messaging.shared.enums.MessageChannel;
import com.timekeeper.bibexpo.messaging.shared.exception.MessagingConfigNotFoundException;
import com.timekeeper.bibexpo.model.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

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

    @ExceptionHandler(MessagingConfigNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(MessagingConfigNotFoundException ex, WebRequest request) {
        log.warn("Default campaign provider not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), request));
    }

    @ExceptionHandler(MessagingProviderException.class)
    public ResponseEntity<ErrorResponse> handleSendFailure(MessagingProviderException ex, WebRequest request) {
        log.error("Default campaign provider call failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ErrorResponse.of(HttpStatus.BAD_GATEWAY, "Bad Gateway", ex.getMessage(), request));
    }
}
