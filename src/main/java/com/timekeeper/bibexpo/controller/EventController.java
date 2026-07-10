package com.timekeeper.bibexpo.controller;

import com.timekeeper.bibexpo.exception.ErrorResponse;
import com.timekeeper.bibexpo.exception.EventAlreadyExistsException;
import com.timekeeper.bibexpo.exception.EventDeletionNotAllowedException;
import com.timekeeper.bibexpo.model.dto.request.AttachUploadRequest;
import com.timekeeper.bibexpo.model.dto.request.CreateEventRequest;
import com.timekeeper.bibexpo.model.dto.request.PresignUploadRequest;
import com.timekeeper.bibexpo.model.dto.request.UpdateEventRequest;
import com.timekeeper.bibexpo.model.dto.response.EventResponse;
import com.timekeeper.bibexpo.model.dto.response.PageableResponse;
import com.timekeeper.bibexpo.model.dto.response.PresignUploadResponse;
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
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

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
    public ResponseEntity<Void> deleteEvent(
            @PathVariable Long id,
            @AuthenticationPrincipal User currentUser) {
        log.info("Received request to delete event with ID: {} by user: {}",
                id, currentUser.getUsername());

        eventService.deleteEvent(id, currentUser);

        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<PresignUploadResponse> createLogoUploadUrl(
            Long id, PresignUploadRequest request, User currentUser) {
        log.info("Request logo upload URL for event ID: {} by user: {}", id, currentUser.getUsername());
        PresignUploadResponse response = eventService.createLogoUploadUrl(
                id, request.getContentType(), currentUser);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<EventResponse> attachLogo(
            Long id, AttachUploadRequest request, User currentUser) {
        log.info("Request to attach logo for event ID: {} by user: {}", id, currentUser.getUsername());
        EventResponse response = eventService.attachLogo(id, request.getObjectKey(), currentUser);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<EventResponse> removeLogo(Long id, User currentUser) {
        log.info("Request to remove logo for event ID: {} by user: {}", id, currentUser.getUsername());
        EventResponse response = eventService.removeLogo(id, currentUser);
        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(EventAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleEventAlreadyExists(
            EventAlreadyExistsException ex, WebRequest request) {
        log.warn("Event already exists: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), request));
    }

    @ExceptionHandler(EventDeletionNotAllowedException.class)
    public ResponseEntity<ErrorResponse> handleEventDeletionNotAllowed(
            EventDeletionNotAllowedException ex, WebRequest request) {
        log.warn("Event deletion not allowed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(), request));
    }
}
