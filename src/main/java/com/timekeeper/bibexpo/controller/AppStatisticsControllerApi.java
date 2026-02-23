package com.timekeeper.bibexpo.controller;

import com.timekeeper.bibexpo.exception.ErrorResponse;
import com.timekeeper.bibexpo.model.dto.response.EventStatisticsResponse;
import com.timekeeper.bibexpo.model.dto.response.OrganizationStatisticsResponse;
import com.timekeeper.bibexpo.model.dto.response.UserStatisticsResponse;
import com.timekeeper.bibexpo.model.entity.User;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * API interface for role-scoped statistics endpoints.
 * Each domain (users, organizations, events) has its own GET and refresh endpoint.
 */
@Tag(name = "Statistics", description = "Role-scoped statistics for users, organizations, and events")
@RequestMapping("/api/statistics")
@SecurityRequirement(name = "bearerAuth")
public interface AppStatisticsControllerApi {

    // -------------------------------------------------------------------------
    // User Statistics
    // -------------------------------------------------------------------------

    @Operation(
            summary = "Get user statistics",
            description = """
                    Returns role-scoped user statistics. \
                    ROOT and ADMIN receive global user counts across the entire system. \
                    ORGANIZER_ADMIN and ORGANIZER_USER receive counts scoped to their own organization. \
                    Snapshot is auto-refreshed when stale (default threshold: 15 minutes)."""
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User statistics retrieved successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = UserStatisticsResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/users")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    ResponseEntity<UserStatisticsResponse> getUserStatistics(
            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser
    );

    @Operation(
            summary = "Force-refresh user statistics",
            description = """
                    Forces immediate recomputation of the user statistics snapshot. \
                    ROOT and ADMIN refresh global counts. ORGANIZER_ADMIN refreshes their org's counts."""
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User statistics refreshed successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = UserStatisticsResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/users/refresh")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN')")
    ResponseEntity<UserStatisticsResponse> refreshUserStatistics(
            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser
    );

    // -------------------------------------------------------------------------
    // Organization Statistics
    // -------------------------------------------------------------------------

    @Operation(
            summary = "Get organization statistics",
            description = """
                    Returns global organization statistics: total, active/inactive counts, \
                    and breakdowns by subscription tier and subscription status. \
                    Restricted to ROOT and ADMIN only. \
                    Snapshot is auto-refreshed when stale (default threshold: 15 minutes)."""
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Organization statistics retrieved successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = OrganizationStatisticsResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden — ROOT or ADMIN role required",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/organizations")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN')")
    ResponseEntity<OrganizationStatisticsResponse> getOrganizationStatistics(
            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser
    );

    @Operation(
            summary = "Force-refresh organization statistics",
            description = "Forces immediate recomputation of the global organization statistics snapshot. ROOT and ADMIN only."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Organization statistics refreshed successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = OrganizationStatisticsResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden — ROOT or ADMIN role required",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/organizations/refresh")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN')")
    ResponseEntity<OrganizationStatisticsResponse> refreshOrganizationStatistics(
            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser
    );

    // -------------------------------------------------------------------------
    // Event Statistics
    // -------------------------------------------------------------------------

    @Operation(
            summary = "Get event statistics",
            description = """
                    Returns role-scoped event statistics: total events, upcoming events, \
                    and breakdown by status (DRAFT, PUBLISHED, CANCELLED, COMPLETED). \
                    ROOT and ADMIN receive global counts. \
                    ORGANIZER_ADMIN and ORGANIZER_USER receive counts for their organization's events. \
                    Snapshot is auto-refreshed when stale (default threshold: 15 minutes)."""
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Event statistics retrieved successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = EventStatisticsResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/events")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    ResponseEntity<EventStatisticsResponse> getEventStatistics(
            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser
    );

    @Operation(
            summary = "Force-refresh event statistics",
            description = """
                    Forces immediate recomputation of the event statistics snapshot. \
                    ROOT and ADMIN refresh global event counts. ORGANIZER_ADMIN refreshes their org's event counts."""
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Event statistics refreshed successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = EventStatisticsResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/events/refresh")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN')")
    ResponseEntity<EventStatisticsResponse> refreshEventStatistics(
            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser
    );
}
