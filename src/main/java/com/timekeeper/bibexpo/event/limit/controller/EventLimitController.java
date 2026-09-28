package com.timekeeper.bibexpo.event.limit.controller;

import com.timekeeper.bibexpo.event.limit.model.dto.request.UpdateEventLimitRequest;
import com.timekeeper.bibexpo.event.limit.model.dto.response.EventLimitResponse;
import com.timekeeper.bibexpo.event.limit.service.EventLimitService;
import com.timekeeper.bibexpo.user.model.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events/{eventId}/limits")
@RequiredArgsConstructor
public class EventLimitController implements EventLimitControllerApi {

    private final EventLimitService eventLimitService;

    @Override
    public ResponseEntity<EventLimitResponse> getEventLimits(
            @PathVariable Long eventId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(eventLimitService.getEventLimits(eventId, currentUser));
    }

    @Override
    public ResponseEntity<EventLimitResponse> updateEventLimits(
            @PathVariable Long eventId,
            @Valid @RequestBody UpdateEventLimitRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(eventLimitService.updateEventLimits(eventId, request, currentUser));
    }
}
