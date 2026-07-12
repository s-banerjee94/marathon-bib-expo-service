package com.timekeeper.bibexpo.messaging.campaign.controller;

import com.timekeeper.bibexpo.exception.ErrorResponse;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.messaging.campaign.exception.InvalidWhatsAppTemplateException;
import com.timekeeper.bibexpo.messaging.campaign.exception.WhatsAppTemplateAlreadyExistsException;
import com.timekeeper.bibexpo.messaging.campaign.model.dto.request.CreateWhatsAppTemplateRequest;
import com.timekeeper.bibexpo.messaging.campaign.model.dto.request.UpdateWhatsAppTemplateRequest;
import com.timekeeper.bibexpo.messaging.campaign.model.dto.response.WhatsAppTemplateResponse;
import com.timekeeper.bibexpo.messaging.campaign.service.WhatsAppTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

import java.util.List;

@RestController
@RequestMapping("/api/events/{eventId}/whatsapp-templates")
@RequiredArgsConstructor
@Slf4j
public class WhatsAppTemplateController implements WhatsAppTemplateControllerApi {

    private final WhatsAppTemplateService templateService;

    @Override
    public ResponseEntity<WhatsAppTemplateResponse> createTemplate(
            @PathVariable Long eventId,
            @RequestBody CreateWhatsAppTemplateRequest request,
            @AuthenticationPrincipal User currentUser) {
        log.info("Received request to create WhatsApp template for event ID: {} by user: {}",
                eventId, currentUser.getUsername());

        WhatsAppTemplateResponse response = templateService.createTemplate(eventId, request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    public ResponseEntity<List<WhatsAppTemplateResponse>> getTemplates(
            @PathVariable Long eventId,
            @RequestParam(required = false) String search,
            @AuthenticationPrincipal User currentUser) {
        log.info("Received request to list WhatsApp templates for event ID: {} by user: {}",
                eventId, currentUser.getUsername());

        return ResponseEntity.ok(templateService.getTemplatesByEvent(eventId, search, currentUser));
    }

    @Override
    public ResponseEntity<WhatsAppTemplateResponse> updateTemplate(
            @PathVariable Long eventId,
            @PathVariable Long templateId,
            @RequestBody UpdateWhatsAppTemplateRequest request,
            @AuthenticationPrincipal User currentUser) {
        log.info("Received request to update WhatsApp template ID: {} for event ID: {} by user: {}",
                templateId, eventId, currentUser.getUsername());

        return ResponseEntity.ok(templateService.updateTemplate(eventId, templateId, request, currentUser));
    }

    @Override
    public ResponseEntity<Void> deleteTemplate(
            @PathVariable Long eventId,
            @PathVariable Long templateId,
            @AuthenticationPrincipal User currentUser) {
        log.info("Received request to delete WhatsApp template ID: {} for event ID: {} by user: {}",
                templateId, eventId, currentUser.getUsername());

        templateService.deleteTemplate(eventId, templateId, currentUser);
        return ResponseEntity.noContent().build();
    }

    // Template not-found is also raised from the campaign service, so it is global (ApiException).
    @ExceptionHandler(WhatsAppTemplateAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleTemplateAlreadyExists(WhatsAppTemplateAlreadyExistsException ex, WebRequest request) {
        log.warn("WhatsApp template conflict: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), request));
    }

    @ExceptionHandler(InvalidWhatsAppTemplateException.class)
    public ResponseEntity<ErrorResponse> handleInvalidTemplate(InvalidWhatsAppTemplateException ex, WebRequest request) {
        log.warn("Invalid WhatsApp template: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(), request));
    }
}
