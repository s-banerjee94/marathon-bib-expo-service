package com.timekeeper.bibexpo.messaging.campaign.controller;

import com.timekeeper.bibexpo.messaging.campaign.exception.InvalidSmsTemplateException;
import com.timekeeper.bibexpo.messaging.campaign.exception.SmsTemplateAlreadyExistsException;
import com.timekeeper.bibexpo.messaging.campaign.model.dto.request.CreateSmsTemplateRequest;
import com.timekeeper.bibexpo.messaging.campaign.model.dto.request.UpdateSmsTemplateRequest;
import com.timekeeper.bibexpo.messaging.campaign.model.dto.response.SmsTemplateResponse;
import com.timekeeper.bibexpo.messaging.campaign.service.SmsTemplateService;
import com.timekeeper.bibexpo.shared.error.ErrorResponse;
import com.timekeeper.bibexpo.user.model.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.util.List;

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
        return ResponseEntity.status(HttpStatus.CREATED).body(smsTemplateService.createSmsTemplate(eventId, request, currentUser));
    }

    @Override
    public ResponseEntity<SmsTemplateResponse> updateSmsTemplate(
            @PathVariable Long eventId,
            @PathVariable Long templateId,
            @Valid @RequestBody UpdateSmsTemplateRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(smsTemplateService.updateSmsTemplate(eventId, templateId, request, currentUser));
    }

    @Override
    public ResponseEntity<List<SmsTemplateResponse>> getSmsTemplatesByEvent(
            @PathVariable Long eventId,
            @RequestParam(required = false) String search,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(smsTemplateService.getSmsTemplatesByEvent(eventId, search, currentUser));
    }

    @Override
    public ResponseEntity<SmsTemplateResponse> getSmsTemplateById(
            @PathVariable Long eventId,
            @PathVariable Long templateId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(smsTemplateService.getSmsTemplateById(eventId, templateId, currentUser));
    }

    @Override
    public ResponseEntity<SmsTemplateResponse> getSmsTemplateBySmsTemplateId(
            @PathVariable Long eventId,
            @PathVariable String smsTemplateId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(smsTemplateService.getSmsTemplateBySmsTemplateId(eventId, smsTemplateId, currentUser));
    }

    @Override
    public ResponseEntity<Void> deleteSmsTemplate(
            @PathVariable Long eventId,
            @PathVariable Long templateId,
            @AuthenticationPrincipal User currentUser) {
        smsTemplateService.deleteSmsTemplate(eventId, templateId, currentUser);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(InvalidSmsTemplateException.class)
    public ResponseEntity<ErrorResponse> handleInvalidSmsTemplate(
            InvalidSmsTemplateException ex, WebRequest request) {
        logRejected(HttpStatus.BAD_REQUEST, ex);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(), request));
    }

    @ExceptionHandler(SmsTemplateAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleSmsTemplateAlreadyExists(
            SmsTemplateAlreadyExistsException ex, WebRequest request) {
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
