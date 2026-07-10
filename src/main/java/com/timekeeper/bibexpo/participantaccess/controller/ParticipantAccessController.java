package com.timekeeper.bibexpo.participantaccess.controller;

import com.timekeeper.bibexpo.exception.ErrorResponse;
import com.timekeeper.bibexpo.exception.InvalidQrCodeException;
import com.timekeeper.bibexpo.model.dto.response.ParticipantDistributionResponse;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.participantaccess.model.dto.request.ScanQrRequest;
import com.timekeeper.bibexpo.participantaccess.model.dto.response.ShortUrlGenerationResponse;
import com.timekeeper.bibexpo.participantaccess.service.ParticipantAccessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

@RestController
@RequestMapping("/api/events/{eventId}/participant-access")
@RequiredArgsConstructor
@Slf4j
public class ParticipantAccessController implements ParticipantAccessControllerApi {

    private final ParticipantAccessService participantAccessService;

    @Override
    public ResponseEntity<Void> generateShortUrls(
            @PathVariable Long eventId,
            @AuthenticationPrincipal User currentUser) {
        log.info("Short URL generation requested for event {} by user {}", eventId, currentUser.getUsername());
        participantAccessService.generateShortUrls(eventId, currentUser);
        return ResponseEntity.accepted().build();
    }

    @Override
    public ResponseEntity<byte[]> getParticipantQr(
            @PathVariable Long eventId,
            @PathVariable String bibNumber,
            @AuthenticationPrincipal User currentUser) {
        log.info("QR code requested for bib {} in event {} by user {}", bibNumber, eventId, currentUser.getUsername());
        byte[] png = participantAccessService.getParticipantQr(eventId, bibNumber, currentUser);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"bib-" + bibNumber + "-qr.png\"")
                .contentType(MediaType.IMAGE_PNG)
                .body(png);
    }

    @Override
    public ResponseEntity<ParticipantDistributionResponse> scanQr(
            @PathVariable Long eventId,
            @RequestBody ScanQrRequest request,
            @AuthenticationPrincipal User currentUser) {
        log.info("QR scan request for event {} by user {}", eventId, currentUser.getUsername());
        ParticipantDistributionResponse response = participantAccessService.scanQr(eventId, request.getCode(), currentUser);
        log.info("QR scan resolved bib {} in event {}", response.getBibNumber(), eventId);
        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(InvalidQrCodeException.class)
    public ResponseEntity<ErrorResponse> handleInvalidQrCode(
            InvalidQrCodeException ex, WebRequest request) {
        log.warn("Invalid QR code: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(), request));
    }
}
