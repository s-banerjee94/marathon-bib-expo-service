package com.timekeeper.bibexpo.controller;

import com.timekeeper.bibexpo.exception.ErrorResponse;
import com.timekeeper.bibexpo.model.dto.request.UpdateEventLimitRequest;
import com.timekeeper.bibexpo.model.dto.response.EventLimitResponse;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "Event Limits", description = "APIs for reading and overriding per-event resource limits")
@SecurityRequirement(name = "bearerAuth")
public interface EventLimitControllerApi {

    @GetMapping
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN')")
    @Operation(
            summary = "Get resource limits for an event",
            description = "Returns the current resource limits for the specified event. ROOT and ADMIN only."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Limits retrieved successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = EventLimitResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Access forbidden",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Event not found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    ResponseEntity<EventLimitResponse> getEventLimits(
            @Parameter(description = "Event ID", required = true)
            @PathVariable Long eventId,
            @AuthenticationPrincipal User currentUser);

    @PatchMapping
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN')")
    @Operation(
            summary = "Override resource limits for an event",
            description = "Partially updates resource limits for the specified event. Only provided fields are changed. ROOT and ADMIN only."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Limits updated successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = EventLimitResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request — limit values must be at least 1",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Access forbidden",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Event not found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    ResponseEntity<EventLimitResponse> updateEventLimits(
            @Parameter(description = "Event ID", required = true)
            @PathVariable Long eventId,
            @Valid @RequestBody UpdateEventLimitRequest request,
            @AuthenticationPrincipal User currentUser);
}
