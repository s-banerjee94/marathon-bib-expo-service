package com.timekeeper.bibexpo.controller;

import com.timekeeper.bibexpo.exception.ErrorResponse;
import com.timekeeper.bibexpo.model.dto.request.CreateRaceRequest;
import com.timekeeper.bibexpo.model.dto.request.UpdateRaceRequest;
import com.timekeeper.bibexpo.model.dto.response.RaceResponse;
import com.timekeeper.bibexpo.model.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Race Management", description = "APIs for managing races within marathon events")
@SecurityRequirement(name = "bearerAuth")
public interface RaceControllerApi {

    @GetMapping
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @Operation(
            summary = "Get all races for an event",
            description = """
                    Retrieve all races for a specific event with optional filtering. \
                    ROOT and ADMIN can access races for any event. \
                    ORGANIZER_ADMIN and ORGANIZER_USER can only access races for events in their organization."""
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Races retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = RaceResponse.class))
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Access forbidden - trying to access event from another organization",
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
    ResponseEntity<List<RaceResponse>> getRacesByEventId(
            @Parameter(description = "Event ID", required = true)
            @PathVariable Long eventId,
            @Parameter(description = "Filter by enabled status (true for enabled only)")
            @RequestParam(required = false) Boolean enabled,
            @AuthenticationPrincipal User currentUser);

    @GetMapping("/{raceId}")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @Operation(
            summary = "Get race by ID",
            description = """
                    Retrieve a specific race by its ID. \
                    ROOT and ADMIN can view any race. \
                    ORGANIZER_ADMIN and ORGANIZER_USER can only view races from their organization's events."""
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Race retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = RaceResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Access forbidden - trying to view race from another organization",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Race or Event not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<RaceResponse> getRaceById(
            @Parameter(description = "Event ID", required = true)
            @PathVariable Long eventId,
            @Parameter(description = "Race ID", required = true)
            @PathVariable Long raceId,
            @AuthenticationPrincipal User currentUser);

    @PostMapping
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @Operation(
            summary = "Create a new race for an event",
            description = """
                    Create a new race within a marathon event. ROOT and ADMIN can create races for any event. \
                    ORGANIZER_ADMIN and ORGANIZER_USER can only create races for events in their organization."""
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Race created successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = RaceResponse.class)
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
                    description = "Access forbidden - insufficient permissions or trying to create race for another organization",
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
                    description = "Race with the same name already exists for this event",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<RaceResponse> createRace(
            @Parameter(description = "Event ID", required = true)
            @PathVariable Long eventId,
            @Valid @RequestBody CreateRaceRequest request,
            @AuthenticationPrincipal User currentUser);

    @PatchMapping("/{raceId}")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @Operation(
            summary = "Update an existing race",
            description = """
                    Update an existing race. Only provided fields will be updated (partial update). \
                    ROOT and ADMIN can update races for any event. \
                    ORGANIZER_ADMIN and ORGANIZER_USER can only update races for events in their organization."""
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Race updated successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = RaceResponse.class)
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
                    description = "Access forbidden - insufficient permissions or trying to update race from another organization",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Race or Event not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Race with the same name already exists for this event",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<RaceResponse> updateRace(
            @Parameter(description = "Event ID", required = true)
            @PathVariable Long eventId,
            @Parameter(description = "Race ID", required = true)
            @PathVariable Long raceId,
            @Valid @RequestBody UpdateRaceRequest request,
            @AuthenticationPrincipal User currentUser);

    @DeleteMapping("/{raceId}")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @Operation(
            summary = "Permanently delete a race",
            description = """
                    Permanently delete a race from the system. \
                    Race can only be deleted if it has no categories. \
                    ROOT and ADMIN can delete any race. \
                    ORGANIZER_ADMIN and ORGANIZER_USER can only delete races from their organization's events."""
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "204",
                    description = "Race deleted successfully"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Race deletion not allowed - race has categories",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Access forbidden - trying to delete race from another organization",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Race or Event not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<Void> deleteRace(
            @Parameter(description = "Event ID", required = true)
            @PathVariable Long eventId,
            @Parameter(description = "Race ID", required = true)
            @PathVariable Long raceId,
            @AuthenticationPrincipal User currentUser);

    @PatchMapping("/{raceId}/toggle-enabled")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @Operation(
            summary = "Toggle race enabled status",
            description = """
                    Toggle the enabled/disabled status of a race. \
                    ROOT and ADMIN can toggle any race. \
                    ORGANIZER_ADMIN and ORGANIZER_USER can only toggle races from their organization's events."""
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Race enabled status toggled successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = RaceResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Access forbidden - trying to toggle race from another organization",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Race or Event not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<RaceResponse> toggleRaceEnabled(
            @Parameter(description = "Event ID", required = true)
            @PathVariable Long eventId,
            @Parameter(description = "Race ID", required = true)
            @PathVariable Long raceId,
            @AuthenticationPrincipal User currentUser);
}
