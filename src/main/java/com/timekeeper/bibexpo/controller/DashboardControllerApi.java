package com.timekeeper.bibexpo.controller;

import com.timekeeper.bibexpo.exception.ErrorResponse;
import com.timekeeper.bibexpo.model.dto.response.dashboard.OrgDashboardResponse;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.enums.DashboardRange;
import com.timekeeper.bibexpo.model.enums.TrendInterval;
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
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Single rollup endpoint for the organizer dashboard.
 * Organization is resolved from the JWT — passing organizationId as a query param is rejected with 400.
 */
@Tag(name = "Dashboard", description = "Org dashboard rollup — one round-trip, one coherent snapshot")
@RequestMapping("/api/dashboard")
@SecurityRequirement(name = "bearerAuth")
public interface DashboardControllerApi {

    @Operation(summary = "Get org dashboard snapshot",
            description = "Returns a single rollup of org profile, event stats, user counts, and trend sparklines. " +
                    "Range applies to stat cards, byStatus, and byCity. User counts are always all-time.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dashboard snapshot returned",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = OrgDashboardResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid query parameters",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/organization")
    @PreAuthorize("hasAnyRole('ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    ResponseEntity<OrgDashboardResponse> getDashboard(
            @Parameter(description = "Date range filter applied to event totals and upcoming count", example = "ALL")
            @RequestParam(defaultValue = "ALL") DashboardRange range,

            @Parameter(description = "Override range for events.byStatus only; omit to use range", example = "MONTH")
            @RequestParam(required = false) DashboardRange statusRange,

            @Parameter(description = "Override range for events.byCity and distinctCities only; omit to use range", example = "YEAR")
            @RequestParam(required = false) DashboardRange citiesRange,

            @Parameter(description = "Number of trend buckets (1–90)", example = "7")
            @RequestParam(defaultValue = "7") int trendBuckets,

            @Parameter(description = "Bucket interval for the trend sparkline", example = "WEEK")
            @RequestParam(defaultValue = "WEEK") TrendInterval trendInterval,

            @Parameter(description = "Top N cities to return in byCity (1–20)", example = "5")
            @RequestParam(defaultValue = "5") int topCities,

            @Parameter(hidden = true)
            @RequestParam(required = false) Long organizationId,

            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser
    );

    @Operation(summary = "Force-refresh the org dashboard snapshot",
            description = "Evicts the cached snapshot and recomputes it. Returns the fresh result.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dashboard snapshot refreshed",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = OrgDashboardResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid query parameters",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/organization/refresh")
    @PreAuthorize("hasAnyRole('ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    ResponseEntity<OrgDashboardResponse> refreshDashboard(
            @Parameter(description = "Date range filter applied to event totals and upcoming count", example = "ALL")
            @RequestParam(defaultValue = "ALL") DashboardRange range,

            @Parameter(description = "Override range for events.byStatus only; omit to use range", example = "MONTH")
            @RequestParam(required = false) DashboardRange statusRange,

            @Parameter(description = "Override range for events.byCity and distinctCities only; omit to use range", example = "YEAR")
            @RequestParam(required = false) DashboardRange citiesRange,

            @Parameter(description = "Number of trend buckets (1–90)", example = "7")
            @RequestParam(defaultValue = "7") int trendBuckets,

            @Parameter(description = "Bucket interval for the trend sparkline", example = "WEEK")
            @RequestParam(defaultValue = "WEEK") TrendInterval trendInterval,

            @Parameter(description = "Top N cities to return in byCity (1–20)", example = "5")
            @RequestParam(defaultValue = "5") int topCities,

            @Parameter(hidden = true)
            @RequestParam(required = false) Long organizationId,

            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser
    );
}
