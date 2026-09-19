package com.timekeeper.bibexpo.reporting.controller;

import com.timekeeper.bibexpo.reporting.model.dto.response.EventDashboardResponse;
import com.timekeeper.bibexpo.reporting.model.enums.EventActivityRange;
import com.timekeeper.bibexpo.shared.error.ErrorResponse;
import com.timekeeper.bibexpo.user.model.entity.User;
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
import org.springframework.web.bind.annotation.PostMapping;
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
                    **Roles:** `ROOT`, `ADMIN`, `ORGANIZER_ADMIN`, `ORGANIZER_USER`, `DISTRIBUTOR`

                    Returns everything the Dashboard tab renders except the Recent Collections feed: \
                    event-wide totals (participants, gender, per-race, per-category) plus a range-scoped \
                    activity block (windowed collected, rate, peak, hourly timeline, top distributors). \

                    **goodies** holds one entry per goodies column of the roster, each with what it was \
                    promised, how much of it has been handed over and that as a percentage, and \
                    underneath it the same three numbers for every distinct value the roster carries, \
                    most participants first. A goody added to the event by hand is on no roster, so it \
                    does not appear. Which item and which variant a value maps to is not here; the \
                    inventory check screen answers that. \

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

    @Operation(summary = "Recount this event's dashboard from the roster",
            description = """
                    **Roles:** `ROOT`, `ADMIN`, `ORGANIZER_ADMIN`

                    Walks every participant of the event and rewrites its counters, then returns \n                    the refreshed rollup.

                    The counters are normally kept in step as participants are created and \n                    deleted, and are recounted automatically at the end of an import, so this is \n                    for the two cases that leaves: an event whose roster predates a counter, and \n                    a suspicion that a number has drifted. It reads the whole roster, so it is a \n                    deliberate action rather than something to call on a timer.""")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Counters rewritten and the rollup returned",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = EventDashboardResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden - user not authorized for this event",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Event not found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{eventId}/dashboard/reconcile")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN')")
    ResponseEntity<EventDashboardResponse> reconcileEventDashboard(
            @Parameter(description = "Event ID", required = true, example = "1")
            @PathVariable Long eventId,

            @Parameter(description = "Activity window for the returned rollup", example = "TODAY")
            @RequestParam(name = "range", defaultValue = "TODAY") EventActivityRange range,

            @AuthenticationPrincipal User currentUser
    );
}
