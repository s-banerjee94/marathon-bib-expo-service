package com.timekeeper.bibexpo.participant.controller;

import com.timekeeper.bibexpo.event.model.dto.response.EventGoodieResponse;
import com.timekeeper.bibexpo.participant.model.dto.request.AddEventGoodieRequest;
import com.timekeeper.bibexpo.participant.service.EventGoodieService;
import com.timekeeper.bibexpo.user.model.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
@Slf4j
public class EventGoodieController implements EventGoodieControllerApi {

    private final EventGoodieService eventGoodieService;

    @Override
    public ResponseEntity<List<EventGoodieResponse>> listGoodies(
            @PathVariable Long eventId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(eventGoodieService.listGoodies(eventId, currentUser));
    }

    @Override
    public ResponseEntity<EventGoodieResponse> addGoodie(
            @PathVariable Long eventId,
            @Valid @RequestBody AddEventGoodieRequest request,
            @AuthenticationPrincipal User currentUser) {
        log.info("Adding goody '{}' to event {} by user {}", request.getName(), eventId, currentUser.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(eventGoodieService.addGoodie(eventId, request, currentUser));
    }

    @Override
    public ResponseEntity<Void> removeGoodie(
            @PathVariable Long eventId,
            @RequestParam String name,
            @AuthenticationPrincipal User currentUser) {
        log.info("Removing goody '{}' from event {} by user {}", name, eventId, currentUser.getUsername());
        eventGoodieService.removeGoodie(eventId, name, currentUser);
        return ResponseEntity.noContent().build();
    }
}
