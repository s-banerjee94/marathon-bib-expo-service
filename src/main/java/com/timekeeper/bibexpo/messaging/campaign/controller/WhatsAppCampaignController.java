package com.timekeeper.bibexpo.messaging.campaign.controller;

import com.timekeeper.bibexpo.messaging.campaign.exception.InvalidWhatsAppCampaignException;
import com.timekeeper.bibexpo.messaging.campaign.exception.WhatsAppCampaignAlreadyActiveException;
import com.timekeeper.bibexpo.messaging.campaign.exception.WhatsAppCampaignNotFoundException;
import com.timekeeper.bibexpo.messaging.campaign.model.dto.request.CreateWhatsAppCampaignRequest;
import com.timekeeper.bibexpo.messaging.campaign.model.dto.request.UpdateWhatsAppCampaignRequest;
import com.timekeeper.bibexpo.messaging.campaign.model.dto.response.WhatsAppCampaignResponse;
import com.timekeeper.bibexpo.messaging.campaign.service.WhatsAppCampaignService;
import com.timekeeper.bibexpo.shared.error.ErrorResponse;
import com.timekeeper.bibexpo.user.model.entity.User;
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
        return ResponseEntity.status(HttpStatus.CREATED).body(campaignService.createCampaign(eventId, request, currentUser));
    }

    @Override
    public ResponseEntity<WhatsAppCampaignResponse> updateCampaign(
            @PathVariable Long eventId,
            @PathVariable Long campaignId,
            @RequestBody UpdateWhatsAppCampaignRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(campaignService.updateCampaign(eventId, campaignId, request, currentUser));
    }

    @Override
    public ResponseEntity<List<WhatsAppCampaignResponse>> getCampaignsByEvent(
            @PathVariable Long eventId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(campaignService.getCampaignsByEvent(eventId, currentUser));
    }

    @Override
    public ResponseEntity<WhatsAppCampaignResponse> disarmCampaign(
            @PathVariable Long eventId,
            @PathVariable Long campaignId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(campaignService.disarmCampaign(eventId, campaignId, currentUser));
    }

    @Override
    public ResponseEntity<Void> deleteCampaign(
            @PathVariable Long eventId,
            @PathVariable Long campaignId,
            @AuthenticationPrincipal User currentUser) {
        campaignService.deleteCampaign(eventId, campaignId, currentUser);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(WhatsAppCampaignNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCampaignNotFound(WhatsAppCampaignNotFoundException ex, WebRequest request) {
        logRejected(HttpStatus.NOT_FOUND, ex);
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), request));
    }

    @ExceptionHandler(InvalidWhatsAppCampaignException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCampaign(InvalidWhatsAppCampaignException ex, WebRequest request) {
        logRejected(HttpStatus.BAD_REQUEST, ex);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(), request));
    }

    @ExceptionHandler(WhatsAppCampaignAlreadyActiveException.class)
    public ResponseEntity<ErrorResponse> handleAlreadyActive(WhatsAppCampaignAlreadyActiveException ex, WebRequest request) {
        logRejected(HttpStatus.CONFLICT, ex);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), request));
    }

    private void logRejected(HttpStatus status, Exception ex) {
        log.atWarn()
                .addKeyValue("event", "request.rejected")
                .addKeyValue("status", status.value())
                .addKeyValue("exception", ex.getClass().getSimpleName())
                .addKeyValue("reason", ex.getMessage())
                .log("Request rejected");
    }
}
