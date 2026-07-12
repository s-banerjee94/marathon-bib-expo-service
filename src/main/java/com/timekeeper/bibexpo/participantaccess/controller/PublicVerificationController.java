package com.timekeeper.bibexpo.participantaccess.controller;

import com.timekeeper.bibexpo.exception.ErrorResponse;
import com.timekeeper.bibexpo.exception.ShortUrlNotFoundException;
import com.timekeeper.bibexpo.participantaccess.model.dto.response.ParticipantVerificationResponse;
import com.timekeeper.bibexpo.participantaccess.service.ParticipantAccessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

@RestController
@RequestMapping("/api/public/short-links")
@RequiredArgsConstructor
@Slf4j
public class PublicVerificationController implements PublicVerificationControllerApi {

    private final ParticipantAccessService participantAccessService;

    @Override
    public ResponseEntity<ParticipantVerificationResponse> resolveShortUrl(@PathVariable String shortCode) {
        log.info("Public verification request for short code {}", shortCode);
        return ResponseEntity.ok(participantAccessService.resolveShortUrl(shortCode));
    }

    @ExceptionHandler(ShortUrlNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleShortUrlNotFound(
            ShortUrlNotFoundException ex, WebRequest request) {
        log.warn("Short URL not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), request));
    }
}
