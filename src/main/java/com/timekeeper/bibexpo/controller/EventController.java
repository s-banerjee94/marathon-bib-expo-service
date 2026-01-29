package com.timekeeper.bibexpo.controller;

import com.timekeeper.bibexpo.model.dto.request.CreateEventRequest;
import com.timekeeper.bibexpo.model.dto.request.UpdateEventRequest;
import com.timekeeper.bibexpo.model.dto.response.EventResponse;
import com.timekeeper.bibexpo.model.dto.response.EventSummaryResponse;
import com.timekeeper.bibexpo.model.dto.response.PageableResponse;
import com.timekeeper.bibexpo.model.entity.EventStatus;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.service.EventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
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
@RequestMapping("/api/events")
@RequiredArgsConstructor
@Slf4j
public class EventController implements EventControllerApi {

    private final EventService eventService;

    @Override
    public ResponseEntity<PageableResponse<EventResponse>> getAllEvents(
            @RequestParam(required = false) Long organizationId,
            @RequestParam(required = false) EventStatus status,
            @RequestParam(required = false) String search,
            Pageable pageable,
            @AuthenticationPrincipal User currentUser) {
        log.info("Received request to get all events by user: {} with filters - organizationId: {}, status: {}, search: {}",
                currentUser.getUsername(), organizationId, status, search);

        Page<EventResponse> eventsPage = eventService.getAllEvents(
                organizationId, status, search, pageable, currentUser);

        PageableResponse<EventResponse> response = PageableResponse.of(eventsPage);

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<PageableResponse<EventResponse>> getOrganizationEvents(
            @RequestParam(required = false) EventStatus status,
            @RequestParam(required = false) String search,
            Pageable pageable,
            @AuthenticationPrincipal User currentUser) {
        log.info("Received request to get organization events by user: {} with filters - status: {}, search: {}",
                currentUser.getUsername(), status, search);

        Page<EventResponse> eventsPage = eventService.getOrganizationEvents(
                status, search, pageable, currentUser);

        PageableResponse<EventResponse> response = PageableResponse.of(eventsPage);

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<EventResponse> getEventById(
            @PathVariable Long id,
            @AuthenticationPrincipal User currentUser) {
        log.info("Received request to get event by ID: {} for user: {}",
                id, currentUser.getUsername());

        EventResponse response = eventService.getEventById(id, currentUser);

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<EventResponse> createEvent(
            @Valid @RequestBody CreateEventRequest request,
            @AuthenticationPrincipal User currentUser) {
        log.info("Received request to create event: {} for organization ID: {} by user: {}",
                request.getEventName(), request.getOrganizationId(), currentUser.getUsername());

        EventResponse response = eventService.createEvent(request, currentUser);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    public ResponseEntity<EventResponse> updateEvent(
            @PathVariable Long id,
            @Valid @RequestBody UpdateEventRequest request,
            @AuthenticationPrincipal User currentUser) {
        log.info("Received request to update event with ID: {} by user: {}",
                id, currentUser.getUsername());

        EventResponse response = eventService.updateEvent(id, request, currentUser);

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<EventResponse> toggleEventEnabled(
            @PathVariable Long id,
            @AuthenticationPrincipal User currentUser) {
        log.info("Received request to toggle enabled status for event with ID: {} by user: {}",
                id, currentUser.getUsername());

        EventResponse response = eventService.toggleEventEnabled(id, currentUser);

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<EventResponse> changeEventStatus(
            @PathVariable Long id,
            @RequestParam EventStatus status,
            @AuthenticationPrincipal User currentUser) {
        log.info("Received request to change status for event with ID: {} to {} by user: {}",
                id, status, currentUser.getUsername());

        EventResponse response = eventService.changeEventStatus(id, status, currentUser);

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<EventSummaryResponse> getEventSummary(
            @PathVariable Long id,
            @AuthenticationPrincipal User currentUser) {
        log.info("Received request to get event summary for event ID: {} by user: {}",
                id, currentUser.getUsername());

        EventSummaryResponse response = eventService.getEventSummary(id, currentUser);

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<Void> deleteEvent(
            @PathVariable Long id,
            @AuthenticationPrincipal User currentUser) {
        log.info("Received request to delete event with ID: {} by user: {}",
                id, currentUser.getUsername());

        eventService.deleteEvent(id, currentUser);

        return ResponseEntity.noContent().build();
    }
}
