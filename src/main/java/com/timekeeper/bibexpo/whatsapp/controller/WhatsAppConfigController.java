package com.timekeeper.bibexpo.whatsapp.controller;

import com.timekeeper.bibexpo.exception.ErrorResponse;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.whatsapp.exception.InvalidWhatsAppConfigException;
import com.timekeeper.bibexpo.whatsapp.exception.WhatsAppConfigNotFoundException;
import com.timekeeper.bibexpo.whatsapp.exception.WhatsAppSendException;
import com.timekeeper.bibexpo.whatsapp.model.dto.request.SaveWhatsAppConfigRequest;
import com.timekeeper.bibexpo.whatsapp.model.dto.request.UpdateWhatsAppSenderModeRequest;
import com.timekeeper.bibexpo.whatsapp.model.dto.request.WhatsAppTestSendRequest;
import com.timekeeper.bibexpo.whatsapp.model.dto.response.WhatsAppConfigResponse;
import com.timekeeper.bibexpo.whatsapp.service.WhatsAppConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

@RestController
@RequestMapping("/api/organizations/{organizationId}/whatsapp-config")
@RequiredArgsConstructor
@Slf4j
public class WhatsAppConfigController implements WhatsAppConfigControllerApi {

    private final WhatsAppConfigService configService;

    @Override
    public ResponseEntity<WhatsAppConfigResponse> getConfig(
            @PathVariable Long organizationId,
            @AuthenticationPrincipal User currentUser) {
        log.info("Received request to get WhatsApp config for organization ID: {} by user: {}",
                organizationId, currentUser.getUsername());

        return ResponseEntity.ok(configService.getConfig(organizationId, currentUser));
    }

    @Override
    public ResponseEntity<WhatsAppConfigResponse> saveConfig(
            @PathVariable Long organizationId,
            @RequestBody SaveWhatsAppConfigRequest request,
            @AuthenticationPrincipal User currentUser) {
        log.info("Received request to save WhatsApp config for organization ID: {} by user: {}",
                organizationId, currentUser.getUsername());

        return ResponseEntity.ok(configService.saveConfig(organizationId, request, currentUser));
    }

    @Override
    public ResponseEntity<WhatsAppConfigResponse> updateMode(
            @PathVariable Long organizationId,
            @RequestBody UpdateWhatsAppSenderModeRequest request,
            @AuthenticationPrincipal User currentUser) {
        log.info("Received request to switch WhatsApp sender mode to {} for organization ID: {} by user: {}",
                request.getMode(), organizationId, currentUser.getUsername());

        return ResponseEntity.ok(configService.updateMode(organizationId, request.getMode(), currentUser));
    }

    @Override
    public ResponseEntity<WhatsAppConfigResponse> testSend(
            @PathVariable Long organizationId,
            @RequestBody WhatsAppTestSendRequest request,
            @AuthenticationPrincipal User currentUser) {
        log.info("Received request to test WhatsApp credentials for organization ID: {} by user: {}",
                organizationId, currentUser.getUsername());

        return ResponseEntity.ok(configService.testSend(organizationId, request, currentUser));
    }

    @Override
    public ResponseEntity<Void> deleteConfig(
            @PathVariable Long organizationId,
            @AuthenticationPrincipal User currentUser) {
        log.info("Received request to delete WhatsApp config for organization ID: {} by user: {}",
                organizationId, currentUser.getUsername());

        configService.deleteConfig(organizationId, currentUser);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(WhatsAppConfigNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleConfigNotFound(WhatsAppConfigNotFoundException ex, WebRequest request) {
        log.warn("WhatsApp config not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), request));
    }

    @ExceptionHandler(InvalidWhatsAppConfigException.class)
    public ResponseEntity<ErrorResponse> handleInvalidConfig(InvalidWhatsAppConfigException ex, WebRequest request) {
        log.warn("WhatsApp config verification failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(), request));
    }

    @ExceptionHandler(WhatsAppSendException.class)
    public ResponseEntity<ErrorResponse> handleSendFailure(WhatsAppSendException ex, WebRequest request) {
        log.error("WhatsApp send failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ErrorResponse.of(HttpStatus.BAD_GATEWAY, "Bad Gateway", ex.getMessage(), request));
    }
}
