package com.timekeeper.bibexpo.controller;

import com.timekeeper.bibexpo.exception.ErrorResponse;
import com.timekeeper.bibexpo.model.dto.response.dashboard.EventDashboardResponse;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.enums.EventActivityRange;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Event-details Dashboard tab rollup. Returns event-wide statistics plus a range-scoped activity
 * block in a single round-trip; the Recent Collections feed is served separately by the existing
 * distribution-logs endpoint.
 */
@Tag(name = "Event Dashboard", description = "Single-round-trip rollup powering the event-details Dashboard tab")
@SecurityRequirement(name = "bearerAuth")
@RequestMapping("/api/events")
public interface EventDashboardControllerApi {

    /**
     * Returns the dashboard rollup for an event.
     *
     * @param eventId     the event
     * @param range       the activity window (defaults to {@code TODAY})
     * @param currentUser the authenticated caller
     * @return the assembled dashboard rollup
     */
    @GetMapping("/{eventId}/dashboard")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER', 'ROLE_DISTRIBUTOR')")
    @Operation(
            summary = "Get the event dashboard rollup",
            description = """
                    Returns everything the Dashboard tab renders except the Recent Collections feed: \
                    event-wide totals (participants, gender, per-race, per-category) plus a range-scoped \
                    activity block (windowed collected, rate, peak, hourly timeline, top distributors). \

                    **range** selects the activity window: `TODAY` (default) returns today's hourly series \
                    with a comparison series for the prior day; `FULL_EXPO` returns one continuous series \
                    across every expo day. Event-wide blocks are constant and do not change with range. \

                    Recent Collections is served by `GET /api/events/{eventId}/distribution/logs`."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Dashboard rollup",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = EventDashboardResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid range value",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden - user not authorized for this event",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Event not found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<EventDashboardResponse> getEventDashboard(
            @Parameter(description = "Event ID", required = true, example = "1")
            @PathVariable Long eventId,

            @Parameter(description = "Activity window", example = "TODAY")
            @RequestParam(name = "range", defaultValue = "TODAY") EventActivityRange range,

            @AuthenticationPrincipal User currentUser
    );
}
