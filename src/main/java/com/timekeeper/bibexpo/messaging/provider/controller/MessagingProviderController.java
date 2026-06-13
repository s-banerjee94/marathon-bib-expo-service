package com.timekeeper.bibexpo.messaging.provider.controller;

import com.timekeeper.bibexpo.exception.ErrorResponse;
import com.timekeeper.bibexpo.messaging.shared.enums.MessageChannel;
import com.timekeeper.bibexpo.messaging.shared.exception.MessagingConfigNotFoundException;
import com.timekeeper.bibexpo.messaging.provider.model.dto.request.SaveMessagingProviderRequest;
import com.timekeeper.bibexpo.messaging.provider.model.dto.response.MessagingProviderResponse;
import com.timekeeper.bibexpo.messaging.provider.service.MessagingProviderAdminService;
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
public class MessagingProviderController implements MessagingProviderControllerApi {

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

    @ExceptionHandler(MessagingConfigNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(MessagingConfigNotFoundException ex, WebRequest request) {
        log.warn("Messaging provider not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), request));
    }
}
