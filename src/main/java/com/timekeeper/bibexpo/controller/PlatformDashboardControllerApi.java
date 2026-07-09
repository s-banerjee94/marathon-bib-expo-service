package com.timekeeper.bibexpo.controller;

import com.timekeeper.bibexpo.exception.ErrorResponse;
import com.timekeeper.bibexpo.model.dto.response.dashboard.PlatformDashboardResponse;
import com.timekeeper.bibexpo.model.dto.response.dashboard.PlatformRevenueResponse;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Global platform overview dashboard for ROOT / ADMIN. One rollup of organization, event,
 * user, and growth-trend stats. Always global — passing an {@code organizationId} is rejected with 400.
 */
@Tag(name = "Platform Dashboard", description = "Global ROOT/ADMIN platform overview rollup")
@RequestMapping("/api/dashboard/platform")
@SecurityRequirement(name = "bearerAuth")
public interface PlatformDashboardControllerApi {

    @Operation(summary = "Get platform dashboard snapshot",
            description = "Returns a single global rollup of organization, event, and user stats plus the platform "
                    + "growth trend. `range` drives KPI numbers and the range-able doughnuts; `tierRange`, `statusRange`, "
                    + "and `citiesRange` override it for their respective sections. Users and trends are always all-time. "
                    + "ROOT or ADMIN only.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dashboard snapshot returned",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = PlatformDashboardResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid query parameters (e.g. organizationId supplied)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden — ROOT or ADMIN role required",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN')")
    ResponseEntity<PlatformDashboardResponse> getDashboard(
            @Parameter(description = "Primary range for KPI cards and range-able doughnuts", example = "ALL")
            @RequestParam(defaultValue = "ALL") DashboardRange range,

            @Parameter(description = "Override range for organizations.byTier only; omit to use range", example = "YEAR")
            @RequestParam(required = false) DashboardRange tierRange,

            @Parameter(description = "Override range for organizations.byStatus and events.byStatus; omit to use range", example = "MONTH")
            @RequestParam(required = false) DashboardRange statusRange,

            @Parameter(description = "Override range for events.byCity and distinctCities; omit to use range", example = "YEAR")
            @RequestParam(required = false) DashboardRange citiesRange,

            @Parameter(description = "Number of trend buckets (1–90)", example = "12")
            @RequestParam(defaultValue = "12") int trendBuckets,

            @Parameter(description = "Bucket interval for the growth trend", example = "MONTH")
            @RequestParam(defaultValue = "MONTH") TrendInterval trendInterval,

            @Parameter(description = "Top N cities to return in events.byCity (1–20)", example = "5")
            @RequestParam(defaultValue = "5") int topCities,

            @Parameter(description = "Top N organizations to return in organizations.top (1–20)", example = "5")
            @RequestParam(defaultValue = "5") int topOrgs,

            @Parameter(hidden = true)
            @RequestParam(required = false) Long organizationId
    );

    @Operation(summary = "Force-refresh the platform dashboard snapshot",
            description = "Evicts the cached snapshot and recomputes it. Returns the fresh result. ROOT or ADMIN only.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dashboard snapshot refreshed",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = PlatformDashboardResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid query parameters (e.g. organizationId supplied)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden — ROOT or ADMIN role required",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/refresh")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN')")
    ResponseEntity<PlatformDashboardResponse> refreshDashboard(
            @Parameter(description = "Primary range for KPI cards and range-able doughnuts", example = "ALL")
            @RequestParam(defaultValue = "ALL") DashboardRange range,

            @Parameter(description = "Override range for organizations.byTier only; omit to use range", example = "YEAR")
            @RequestParam(required = false) DashboardRange tierRange,

            @Parameter(description = "Override range for organizations.byStatus and events.byStatus; omit to use range", example = "MONTH")
            @RequestParam(required = false) DashboardRange statusRange,

            @Parameter(description = "Override range for events.byCity and distinctCities; omit to use range", example = "YEAR")
            @RequestParam(required = false) DashboardRange citiesRange,

            @Parameter(description = "Number of trend buckets (1–90)", example = "12")
            @RequestParam(defaultValue = "12") int trendBuckets,

            @Parameter(description = "Bucket interval for the growth trend", example = "MONTH")
            @RequestParam(defaultValue = "MONTH") TrendInterval trendInterval,

            @Parameter(description = "Top N cities to return in events.byCity (1–20)", example = "5")
            @RequestParam(defaultValue = "5") int topCities,

            @Parameter(description = "Top N organizations to return in organizations.top (1–20)", example = "5")
            @RequestParam(defaultValue = "5") int topOrgs,

            @Parameter(hidden = true)
            @RequestParam(required = false) Long organizationId
    );

    @Operation(summary = "Get platform revenue summary",
            description = "Revenue collected (paid) across the platform: a headline figure for the range, a signed "
                    + "delta vs the previous equal-length period, and per-bucket bars. \"Earned\" = money collected "
                    + "(FINAL bills marked PAID), summed by payment date. ROOT or ADMIN only.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Revenue summary returned (zeroed when no data)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = PlatformRevenueResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden — ROOT or ADMIN role required",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/revenue")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN')")
    ResponseEntity<PlatformRevenueResponse> getRevenue(
            @Parameter(description = "Range for the headline figure and delta", example = "ALL")
            @RequestParam(defaultValue = "ALL") DashboardRange range,

            @Parameter(description = "Number of trend bars (1–90)", example = "12")
            @RequestParam(defaultValue = "12") int trendBuckets,

            @Parameter(description = "Bucket interval for the bars", example = "MONTH")
            @RequestParam(defaultValue = "MONTH") TrendInterval trendInterval
    );
}
