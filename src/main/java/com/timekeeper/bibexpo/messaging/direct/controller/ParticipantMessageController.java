package com.timekeeper.bibexpo.messaging.direct.controller;

import com.timekeeper.bibexpo.messaging.direct.model.dto.request.SendParticipantMessagesRequest;
import com.timekeeper.bibexpo.messaging.direct.model.dto.response.ParticipantMessagesResponse;
import com.timekeeper.bibexpo.messaging.direct.service.ParticipantMessageService;
import com.timekeeper.bibexpo.model.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events/{eventId}/participant-messages")
@RequiredArgsConstructor
@Slf4j
public class ParticipantMessageController implements ParticipantMessageControllerApi {

    private final ParticipantMessageService participantMessageService;

    @Override
    public ResponseEntity<ParticipantMessagesResponse> sendToParticipants(
            @PathVariable Long eventId,
            @Valid @RequestBody SendParticipantMessagesRequest request,
            @AuthenticationPrincipal User currentUser) {
        log.info("Received request to send {} to {} participant(s) of event ID: {} by user: {}",
                request.getChannel(), request.getBibNumbers().size(), eventId, currentUser.getUsername());
        return ResponseEntity.ok(participantMessageService.sendToParticipants(eventId, request, currentUser));
    }
}
