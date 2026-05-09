package com.timekeeper.bibexpo.controller;

import com.timekeeper.bibexpo.model.dto.request.CreateSmsCampaignRequest;
import com.timekeeper.bibexpo.model.dto.request.UpdateSmsCampaignRequest;
import com.timekeeper.bibexpo.model.dto.response.PageableResponse;
import com.timekeeper.bibexpo.model.dto.response.SmsCampaignResponse;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.service.SmsCampaignService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

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
    public ResponseEntity<PageableResponse<SmsCampaignResponse>> getCampaignsByEvent(
            @PathVariable Long eventId,
            Pageable pageable,
            @AuthenticationPrincipal User currentUser) {
        log.info("Received request to get SMS campaigns for event ID: {} by user: {}", eventId, currentUser.getUsername());
        return ResponseEntity.ok(PageableResponse.of(smsCampaignService.getCampaignsByEvent(eventId, pageable, currentUser)));
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
    public ResponseEntity<SmsCampaignResponse> deactivateCampaign(
            @PathVariable Long eventId,
            @PathVariable Long campaignId,
            @AuthenticationPrincipal User currentUser) {
        log.info("Received request to deactivate SMS campaign ID: {} for event ID: {} by user: {}", campaignId, eventId, currentUser.getUsername());
        return ResponseEntity.ok(smsCampaignService.deactivateCampaign(eventId, campaignId, currentUser));
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
}
