package com.timekeeper.bibexpo.messaging.campaign.controller;

import com.timekeeper.bibexpo.exception.ErrorResponse;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.messaging.campaign.exception.InvalidWhatsAppCampaignException;
import com.timekeeper.bibexpo.messaging.campaign.exception.WhatsAppCampaignAlreadyActiveException;
import com.timekeeper.bibexpo.messaging.campaign.exception.WhatsAppCampaignNotFoundException;
import com.timekeeper.bibexpo.messaging.campaign.model.dto.request.CreateWhatsAppCampaignRequest;
import com.timekeeper.bibexpo.messaging.campaign.model.dto.request.UpdateWhatsAppCampaignRequest;
import com.timekeeper.bibexpo.messaging.campaign.model.dto.response.WhatsAppCampaignResponse;
import com.timekeeper.bibexpo.messaging.campaign.service.WhatsAppCampaignService;
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

import java.util.List;

@RestController
@RequestMapping("/api/events/{eventId}/whatsapp-campaigns")
@RequiredArgsConstructor
@Slf4j
public class WhatsAppCampaignController implements WhatsAppCampaignControllerApi {

    private final WhatsAppCampaignService campaignService;

    @Override
    public ResponseEntity<WhatsAppCampaignResponse> createCampaign(
            @PathVariable Long eventId,
            @RequestBody CreateWhatsAppCampaignRequest request,
            @AuthenticationPrincipal User currentUser) {
        log.info("Received request to create WhatsApp campaign for event ID: {} by user: {}", eventId, currentUser.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(campaignService.createCampaign(eventId, request, currentUser));
    }

    @Override
    public ResponseEntity<WhatsAppCampaignResponse> updateCampaign(
            @PathVariable Long eventId,
            @PathVariable Long campaignId,
            @RequestBody UpdateWhatsAppCampaignRequest request,
            @AuthenticationPrincipal User currentUser) {
        log.info("Received request to update WhatsApp campaign ID: {} for event ID: {} by user: {}", campaignId, eventId, currentUser.getUsername());
        return ResponseEntity.ok(campaignService.updateCampaign(eventId, campaignId, request, currentUser));
    }

    @Override
    public ResponseEntity<List<WhatsAppCampaignResponse>> getCampaignsByEvent(
            @PathVariable Long eventId,
            @AuthenticationPrincipal User currentUser) {
        log.info("Received request to get WhatsApp campaigns for event ID: {} by user: {}", eventId, currentUser.getUsername());
        return ResponseEntity.ok(campaignService.getCampaignsByEvent(eventId, currentUser));
    }

    @Override
    public ResponseEntity<WhatsAppCampaignResponse> disarmCampaign(
            @PathVariable Long eventId,
            @PathVariable Long campaignId,
            @AuthenticationPrincipal User currentUser) {
        log.info("Received request to disarm WhatsApp campaign ID: {} for event ID: {} by user: {}", campaignId, eventId, currentUser.getUsername());
        return ResponseEntity.ok(campaignService.disarmCampaign(eventId, campaignId, currentUser));
    }

    @Override
    public ResponseEntity<Void> deleteCampaign(
            @PathVariable Long eventId,
            @PathVariable Long campaignId,
            @AuthenticationPrincipal User currentUser) {
        log.info("Received request to delete WhatsApp campaign ID: {} for event ID: {} by user: {}", campaignId, eventId, currentUser.getUsername());
        campaignService.deleteCampaign(eventId, campaignId, currentUser);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(WhatsAppCampaignNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCampaignNotFound(WhatsAppCampaignNotFoundException ex, WebRequest request) {
        log.warn("WhatsApp campaign not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), request));
    }

    @ExceptionHandler(InvalidWhatsAppCampaignException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCampaign(InvalidWhatsAppCampaignException ex, WebRequest request) {
        log.warn("Invalid WhatsApp campaign request: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(), request));
    }

    @ExceptionHandler(WhatsAppCampaignAlreadyActiveException.class)
    public ResponseEntity<ErrorResponse> handleAlreadyActive(WhatsAppCampaignAlreadyActiveException ex, WebRequest request) {
        log.warn("WhatsApp campaign conflict: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), request));
    }
}
