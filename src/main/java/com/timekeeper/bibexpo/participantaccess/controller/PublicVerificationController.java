package com.timekeeper.bibexpo.participantaccess.controller;

import com.timekeeper.bibexpo.participantaccess.model.dto.response.ParticipantVerificationResponse;
import com.timekeeper.bibexpo.participantaccess.service.ParticipantAccessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/s")
@RequiredArgsConstructor
@Slf4j
public class PublicVerificationController implements PublicVerificationControllerApi {

    private final ParticipantAccessService participantAccessService;

    @Override
    public ResponseEntity<ParticipantVerificationResponse> resolveShortUrl(@PathVariable String shortCode) {
        log.info("Public verification request for short code {}", shortCode);
        return ResponseEntity.ok(participantAccessService.resolveShortUrl(shortCode));
    }
}
