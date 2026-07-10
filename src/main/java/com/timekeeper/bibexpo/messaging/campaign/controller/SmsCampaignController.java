package com.timekeeper.bibexpo.messaging.campaign.controller;

import com.timekeeper.bibexpo.exception.ErrorResponse;
import com.timekeeper.bibexpo.messaging.campaign.exception.InvalidSmsCampaignException;
import com.timekeeper.bibexpo.messaging.campaign.exception.SmsCampaignAlreadyActiveException;
import com.timekeeper.bibexpo.messaging.campaign.exception.SmsCampaignNotFoundException;
import com.timekeeper.bibexpo.messaging.campaign.model.dto.request.CreateSmsCampaignRequest;
import com.timekeeper.bibexpo.messaging.campaign.model.dto.request.UpdateSmsCampaignRequest;
import com.timekeeper.bibexpo.messaging.campaign.model.dto.response.SmsCampaignResponse;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.messaging.campaign.service.SmsCampaignService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;

@RestController
@RequestMapping("/api/events/{eventId}/sms-campaigns")
@RequiredArgsConstructor
@Slf4j
public class SmsCampaignController implements SmsCampaignControllerApi {

    private final SmsCampaignService smsCampaignService;

    @Override
    public ResponseEntity<SmsCampaignResponse> createCampaign(
            @PathVariable Long eventId,
            @Valid @RequestBody CreateSmsCampaignRequest request,
            @AuthenticationPrincipal User currentUser) {
        log.info("Received request to create SMS campaign for event ID: {} by user: {}", eventId, currentUser.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(smsCampaignService.createCampaign(eventId, request, currentUser));
    }

    @Override
    public ResponseEntity<SmsCampaignResponse> updateCampaign(
            @PathVariable Long eventId,
            @PathVariable Long campaignId,
            @Valid @RequestBody UpdateSmsCampaignRequest request,
            @AuthenticationPrincipal User currentUser) {
        log.info("Received request to update SMS campaign ID: {} for event ID: {} by user: {}", campaignId, eventId, currentUser.getUsername());
        return ResponseEntity.ok(smsCampaignService.updateCampaign(eventId, campaignId, request, currentUser));
    }

    @Override
    public ResponseEntity<List<SmsCampaignResponse>> getCampaignsByEvent(
            @PathVariable Long eventId,
            @AuthenticationPrincipal User currentUser) {
        log.info("Received request to get SMS campaigns for event ID: {} by user: {}", eventId, currentUser.getUsername());
        return ResponseEntity.ok(smsCampaignService.getCampaignsByEvent(eventId, currentUser));
    }

    @Override
    public ResponseEntity<SmsCampaignResponse> getCampaignById(
            @PathVariable Long eventId,
            @PathVariable Long campaignId,
            @AuthenticationPrincipal User currentUser) {
        log.info("Received request to get SMS campaign ID: {} for event ID: {} by user: {}", campaignId, eventId, currentUser.getUsername());
        return ResponseEntity.ok(smsCampaignService.getCampaignById(eventId, campaignId, currentUser));
    }

    @Override
    public ResponseEntity<SmsCampaignResponse> disarmCampaign(
            @PathVariable Long eventId,
            @PathVariable Long campaignId,
            @AuthenticationPrincipal User currentUser) {
        log.info("Received request to disarm SMS campaign ID: {} for event ID: {} by user: {}", campaignId, eventId, currentUser.getUsername());
        return ResponseEntity.ok(smsCampaignService.disarmCampaign(eventId, campaignId, currentUser));
    }

    @Override
    public ResponseEntity<Void> deleteCampaign(
            @PathVariable Long eventId,
            @PathVariable Long campaignId,
            @AuthenticationPrincipal User currentUser) {
        log.info("Received request to delete SMS campaign ID: {} for event ID: {} by user: {}", campaignId, eventId, currentUser.getUsername());
        smsCampaignService.deleteCampaign(eventId, campaignId, currentUser);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(SmsCampaignNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleSmsCampaignNotFound(
            SmsCampaignNotFoundException ex, WebRequest request) {
        log.warn("SMS campaign not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), request));
    }

    @ExceptionHandler(SmsCampaignAlreadyActiveException.class)
    public ResponseEntity<ErrorResponse> handleSmsCampaignAlreadyActive(
            SmsCampaignAlreadyActiveException ex, WebRequest request) {
        log.warn("SMS campaign conflict: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), request));
    }

    @ExceptionHandler(InvalidSmsCampaignException.class)
    public ResponseEntity<ErrorResponse> handleInvalidSmsCampaign(
            InvalidSmsCampaignException ex, WebRequest request) {
        log.warn("Invalid SMS campaign operation: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(), request));
    }
}
