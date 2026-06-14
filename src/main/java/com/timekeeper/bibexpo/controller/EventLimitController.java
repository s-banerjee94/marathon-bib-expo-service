package com.timekeeper.bibexpo.controller;

import com.timekeeper.bibexpo.model.dto.request.UpdateEventLimitRequest;
import com.timekeeper.bibexpo.model.dto.response.EventLimitResponse;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.service.EventLimitService;
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
@RequestMapping("/api/events/{eventId}/limits")
@RequiredArgsConstructor
@Slf4j
public class EventLimitController implements EventLimitControllerApi {

    private final EventLimitService eventLimitService;

    @Override
    public ResponseEntity<EventLimitResponse> getEventLimits(
            @PathVariable Long eventId,
            @AuthenticationPrincipal User currentUser) {
        log.info("Get limits for event {} by user {}", eventId, currentUser.getUsername());
        return ResponseEntity.ok(eventLimitService.getEventLimits(eventId, currentUser));
    }

    @Override
    public ResponseEntity<EventLimitResponse> updateEventLimits(
            @PathVariable Long eventId,
            @Valid @RequestBody UpdateEventLimitRequest request,
            @AuthenticationPrincipal User currentUser) {
        log.info("Update limits for event {} by user {}", eventId, currentUser.getUsername());
        return ResponseEntity.ok(eventLimitService.updateEventLimits(eventId, request, currentUser));
    }
}
