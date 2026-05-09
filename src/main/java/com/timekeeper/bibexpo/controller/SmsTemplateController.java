package com.timekeeper.bibexpo.controller;

import com.timekeeper.bibexpo.model.dto.request.CreateSmsTemplateRequest;
import com.timekeeper.bibexpo.model.dto.request.UpdateSmsTemplateRequest;
import com.timekeeper.bibexpo.model.dto.response.PageableResponse;
import com.timekeeper.bibexpo.model.dto.response.SmsTemplateResponse;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.service.SmsTemplateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events/{eventId}/sms-templates")
@RequiredArgsConstructor
@Slf4j
public class SmsTemplateController implements SmsTemplateControllerApi {

    private final SmsTemplateService smsTemplateService;

    @Override
    public ResponseEntity<SmsTemplateResponse> createSmsTemplate(
            @PathVariable Long eventId,
            @Valid @RequestBody CreateSmsTemplateRequest request,
            @AuthenticationPrincipal User currentUser) {
        log.info("Received request to create SMS template for event ID: {} by user: {}",
                eventId, currentUser.getUsername());

        SmsTemplateResponse response = smsTemplateService.createSmsTemplate(eventId, request, currentUser);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    public ResponseEntity<SmsTemplateResponse> updateSmsTemplate(
            @PathVariable Long eventId,
            @PathVariable Long templateId,
            @Valid @RequestBody UpdateSmsTemplateRequest request,
            @AuthenticationPrincipal User currentUser) {
        log.info("Received request to update SMS template ID: {} for event ID: {} by user: {}",
                templateId, eventId, currentUser.getUsername());

        SmsTemplateResponse response = smsTemplateService.updateSmsTemplate(eventId, templateId, request, currentUser);

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<PageableResponse<SmsTemplateResponse>> getSmsTemplatesByEvent(
            @PathVariable Long eventId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean enabled,
            Pageable pageable,
            @AuthenticationPrincipal User currentUser) {
        log.info("Received request to get SMS templates for event ID: {} search: {} enabled: {} by user: {}",
                eventId, search, enabled, currentUser.getUsername());

        PageableResponse<SmsTemplateResponse> response = PageableResponse.of(
                smsTemplateService.getSmsTemplatesByEvent(eventId, search, enabled, pageable, currentUser));

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<SmsTemplateResponse> getSmsTemplateById(
            @PathVariable Long eventId,
            @PathVariable Long templateId,
            @AuthenticationPrincipal User currentUser) {
        log.info("Received request to get SMS template ID: {} for event ID: {} by user: {}",
                templateId, eventId, currentUser.getUsername());

        SmsTemplateResponse response = smsTemplateService.getSmsTemplateById(eventId, templateId, currentUser);

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<SmsTemplateResponse> getSmsTemplateBySmsTemplateId(
            @PathVariable Long eventId,
            @PathVariable String smsTemplateId,
            @AuthenticationPrincipal User currentUser) {
        log.info("Received request to get SMS template by DLT ID: {} for event ID: {} by user: {}",
                smsTemplateId, eventId, currentUser.getUsername());

        SmsTemplateResponse response = smsTemplateService.getSmsTemplateBySmsTemplateId(eventId, smsTemplateId, currentUser);

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<SmsTemplateResponse> toggleSmsTemplateEnabled(
            @PathVariable Long eventId,
            @PathVariable Long templateId,
            @AuthenticationPrincipal User currentUser) {
        log.info("Received request to toggle SMS template ID: {} enabled status for event ID: {} by user: {}",
                templateId, eventId, currentUser.getUsername());

        SmsTemplateResponse response = smsTemplateService.toggleSmsTemplateEnabled(eventId, templateId, currentUser);

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<Void> deleteSmsTemplate(
            @PathVariable Long eventId,
            @PathVariable Long templateId,
            @AuthenticationPrincipal User currentUser) {
        log.info("Received request to delete SMS template ID: {} for event ID: {} by user: {}",
                templateId, eventId, currentUser.getUsername());

        smsTemplateService.deleteSmsTemplate(eventId, templateId, currentUser);

        return ResponseEntity.noContent().build();
    }
}
