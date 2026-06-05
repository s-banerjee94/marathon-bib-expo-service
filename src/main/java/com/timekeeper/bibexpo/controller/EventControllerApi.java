package com.timekeeper.bibexpo.controller;

import com.timekeeper.bibexpo.exception.ErrorResponse;
import com.timekeeper.bibexpo.model.dto.request.AttachUploadRequest;
import com.timekeeper.bibexpo.model.dto.request.CreateEventRequest;
import com.timekeeper.bibexpo.model.dto.request.PresignUploadRequest;
import com.timekeeper.bibexpo.model.dto.request.UpdateEventRequest;
import com.timekeeper.bibexpo.model.dto.response.EventResponse;
import com.timekeeper.bibexpo.model.dto.response.PageableResponse;
import com.timekeeper.bibexpo.model.dto.response.PresignUploadResponse;
import com.timekeeper.bibexpo.model.entity.EventStatus;
import com.timekeeper.bibexpo.model.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "Event Management", description = "APIs for managing marathon events")
@SecurityRequirement(name = "bearerAuth")
public interface EventControllerApi {

    @GetMapping
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN')")
    @Operation(
            summary = "Get all events in the system (ROOT/ADMIN only)",
            description = """
                    Retrieve all events with optional filtering, search, and pagination. \
                    Only ROOT and ADMIN can access this endpoint. \
                    Can query any organization and include deleted events."""
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Events retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PageableResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Access forbidden - requires ROOT or ADMIN",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<PageableResponse<EventResponse>> getAllEvents(
            @Parameter(description = "Filter by organization ID")
            @RequestParam(required = false) Long organizationId,
            @Parameter(description = "Filter by event status (DRAFT, PUBLISHED, CANCELLED, COMPLETED)")
            @RequestParam(required = false) EventStatus status,
            @Parameter(description = "Search across event name, description, and venue name (partial match, case-insensitive)")
            @RequestParam(required = false) String search,
            @Parameter(description = "Pagination and sorting parameters")
            Pageable pageable,
            @AuthenticationPrincipal User currentUser);

    @GetMapping("/organization")
    @PreAuthorize("hasAnyRole('ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER', 'ROLE_DISTRIBUTOR')")
    @Operation(
            summary = "Get events in current user's organization",
            description = """
                    Retrieve all events in the current user's organization with optional filtering, search, and pagination. \
                    Only ORGANIZER_ADMIN, ORGANIZER_USER and ROLE_DISTRIBUTOR can access this endpoint. \
                    Automatically scoped to the user's organization."""
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Organization events retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PageableResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Access forbidden - user is not an organization user or has no organization assigned",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<PageableResponse<EventResponse>> getOrganizationEvents(
            @Parameter(description = "Filter by event status (DRAFT, PUBLISHED, CANCELLED, COMPLETED)")
            @RequestParam(required = false) EventStatus status,
            @Parameter(description = "Search across event name, description, and venue name (partial match, case-insensitive)")
            @RequestParam(required = false) String search,
            @Parameter(description = "Pagination and sorting parameters")
            Pageable pageable,
            @AuthenticationPrincipal User currentUser);

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER', 'ROLE_DISTRIBUTOR')")
    @Operation(
            summary = "Get event by ID",
            description = """
                    Retrieve a single event by its ID. \
                    ROOT and ADMIN can view any event. \
                    ORGANIZER_ADMIN and ORGANIZER_USER can only view events from their own organization. \
                    DISTRIBUTOR can view events from their own organization."""
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Event retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = EventResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Access forbidden - trying to view event from another organization",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Event not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<EventResponse> getEventById(
            @PathVariable Long id,
            @AuthenticationPrincipal User currentUser);

    @PostMapping
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @Operation(
            summary = "Create a new event",
            description = """
                    Create a new marathon event. ROOT and ADMIN can create events for any organization. \
                    ORGANIZER_ADMIN and ORGANIZER_USER can only create events for their own organization."""
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Event created successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = EventResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request data",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Access forbidden - insufficient permissions or trying to create event for another organization",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Organization not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Event with the same name already exists for this organization",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<EventResponse> createEvent(
            @Valid @RequestBody CreateEventRequest request,
            @AuthenticationPrincipal User currentUser);

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @Operation(
            summary = "Update an existing event",
            description = """
                    Update an existing marathon event. Only provided fields will be updated (partial update). \
                    Event status is not changed here; use the PATCH /{id}/status endpoint. \
                    Timezone cannot be changed once the event is PUBLISHED or COMPLETED. \
                    Once PUBLISHED the start date is locked and the end date can only be extended (never moved earlier); when COMPLETED, dates cannot be changed. \
                    Permanent deletion is handled by a separate DELETE endpoint. \
                    ROOT and ADMIN can update events for any organization. \
                    ORGANIZER_ADMIN and ORGANIZER_USER can only update events for their own organization."""
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Event updated successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = EventResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request data",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Access forbidden - insufficient permissions or trying to update event from another organization",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Event not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Event with the same name already exists for this organization",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<EventResponse> updateEvent(
            @PathVariable Long id,
            @Valid @RequestBody UpdateEventRequest request,
            @AuthenticationPrincipal User currentUser);

    @PatchMapping("/{id}/toggle-enabled")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN')")
    @Operation(
            summary = "Toggle event enabled status (ROOT/ADMIN only)",
            description = """
                    Toggle the enabled/disabled status of an event. \
                    Only ROOT and ADMIN can toggle event status. \
                    When an event is disabled, all event and child entity endpoints become inaccessible."""
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Event enabled status toggled successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = EventResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Access forbidden - only ROOT and ADMIN can toggle event status",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Event not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<EventResponse> toggleEventEnabled(
            @PathVariable Long id,
            @AuthenticationPrincipal User currentUser);

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @Operation(
            summary = "Change event status",
            description = """
                    Change event status. Allowed moves: DRAFT to PUBLISHED or CANCELLED; \
                    PUBLISHED to DRAFT (only before distribution and before the start date), COMPLETED, or CANCELLED. \
                    Once distribution has started or the start date has passed, the event can no longer return to DRAFT. \
                    A COMPLETED or CANCELLED event is final and can only be reopened to PUBLISHED by ROOT or ADMIN. \
                    DISTRIBUTOR cannot change event status; ORGANIZER_ADMIN and ORGANIZER_USER are limited to events in their own organization."""
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Event status changed successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = EventResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid status value, or the requested status change is not allowed",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Access forbidden - insufficient permissions, another organization's event, or reopening a finished event without administrator rights",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Event not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<EventResponse> changeEventStatus(
            @PathVariable Long id,
            @Parameter(description = "New event status (DRAFT, PUBLISHED, CANCELLED, COMPLETED)", required = true)
            @RequestParam EventStatus status,
            @AuthenticationPrincipal User currentUser);

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @Operation(
            summary = "Permanently delete an event",
            description = """
                    Permanently delete an event from the system. \
                    Event can only be deleted if: \
                    1) Status is DRAFT or CANCELLED, \
                    2) Participant list is empty (TODO: not yet implemented). \
                    ROOT and ADMIN can delete any event. \
                    ORGANIZER_ADMIN and ORGANIZER_USER can only delete events from their own organization."""
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "204",
                    description = "Event deleted successfully"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Event deletion not allowed - status is not DRAFT or CANCELLED, or participant list is not empty",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Access forbidden - trying to delete event from another organization",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Event not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<Void> deleteEvent(
            @PathVariable Long id,
            @AuthenticationPrincipal User currentUser);

    @PostMapping("/{id}/logo/upload-url")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @Operation(
            summary = "Get a presigned URL to upload an event logo",
            description = """
                    Returns a short-lived presigned S3 PUT URL. The client uploads the file bytes \
                    directly to that URL with the given Content-Type, then calls \
                    PUT /api/events/{id}/logo with the returned objectKey to attach it. \
                    Same authorization as updating the event."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Presigned upload URL created",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = PresignUploadResponse.class))),
            @ApiResponse(responseCode = "400", description = "Unsupported file type",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Event not found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<PresignUploadResponse> createLogoUploadUrl(
            @PathVariable Long id,
            @Valid @RequestBody PresignUploadRequest request,
            @AuthenticationPrincipal User currentUser);

    @PutMapping("/{id}/logo")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @Operation(
            summary = "Attach an uploaded event logo",
            description = """
                    Confirms a completed upload and sets it as the event's logo. The object key \
                    must belong to this event and the object must exist in S3. Any previous logo \
                    is deleted. Same authorization as updating the event."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Logo attached",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = EventResponse.class))),
            @ApiResponse(responseCode = "400", description = "Object key invalid or file missing",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Event not found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<EventResponse> attachLogo(
            @PathVariable Long id,
            @Valid @RequestBody AttachUploadRequest request,
            @AuthenticationPrincipal User currentUser);

    @DeleteMapping("/{id}/logo")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @Operation(
            summary = "Remove the event logo",
            description = "Deletes the event's logo from storage. Same authorization as updating the event."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Logo removed",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = EventResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Event not found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<EventResponse> removeLogo(
            @PathVariable Long id,
            @AuthenticationPrincipal User currentUser);
}
