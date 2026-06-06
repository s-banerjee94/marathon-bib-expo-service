package com.timekeeper.bibexpo.billing.controller;

import com.timekeeper.bibexpo.exception.ErrorResponse;
import com.timekeeper.bibexpo.billing.model.dto.request.UpdatePaymentStatusRequest;
import com.timekeeper.bibexpo.billing.model.dto.response.BillResponse;
import com.timekeeper.bibexpo.model.dto.response.PageableResponse;
import com.timekeeper.bibexpo.billing.model.dto.response.BillingSummaryResponse;
import com.timekeeper.bibexpo.billing.model.entity.PaymentStatus;
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
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Instant;

@Tag(name = "Billing Administration",
        description = "Organization and platform billing rollups, the global bill feed, and payment-state management")
@SecurityRequirement(name = "bearerAuth")
public interface GlobalBillingControllerApi {

    /**
     * List every bill across all organizations, filtered and paginated
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN')")
    @Operation(
            summary = "List all bills (global feed)",
            description = """
                    The master feed of every bill across all organizations, paginated and filterable.

                    **Response:** a `PageableResponse` whose `content[]` is an array of `BillResponse` \
                    objects (same schema as the per-event and per-organization bill lists), plus \
                    `page`, `size`, `totalElements`, `totalPages`, `first`, `last`.

                    **Pagination & sorting:** standard Spring Data — `page` (0-indexed), `size`, and \
                    `sort` (e.g. `sort=createdAt,desc`). When no `sort` is given it defaults to \
                    `createdAt,desc`. Sortable fields are `BillResponse` properties such as \
                    `createdAt`, `totalAmount`, `participantCount`.

                    **Filters** (all optional, AND-combined): `organizationId`, `eventId`, \
                    `from`/`to` (inclusive range on `createdAt`), `reason`, `paymentStatus`, and \
                    `q` (case-insensitive substring of organizer name or event name).

                    Only accessible by ROOT or ADMIN."""
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Bills retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PageableResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid query parameter",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
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
    ResponseEntity<PageableResponse<BillResponse>> listAllBills(
            @Parameter(description = "Filter by organization id", example = "7")
            @RequestParam(required = false) Long organizationId,
            @Parameter(description = "Filter by event id", example = "42")
            @RequestParam(required = false) Long eventId,
            @Parameter(description = "Inclusive lower bound on createdAt (ISO-8601 instant)", example = "2026-01-01T00:00:00Z")
            @RequestParam(required = false) Instant from,
            @Parameter(description = "Inclusive upper bound on createdAt (ISO-8601 instant)", example = "2026-12-31T23:59:59Z")
            @RequestParam(required = false) Instant to,
            @Parameter(description = "Trigger filter", schema = @Schema(allowableValues = {"AUTO", "MANUAL"}))
            @RequestParam(required = false) String reason,
            @Parameter(description = "Payment-state filter")
            @RequestParam(required = false) PaymentStatus paymentStatus,
            @Parameter(description = "Case-insensitive substring of organizer name or event name", example = "mumbai")
            @RequestParam(required = false) String q,
            Pageable pageable);

    /**
     * Platform billing aggregates for KPI tiles and charts
     */
    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN')")
    @Operation(
            summary = "Global billing summary",
            description = """
                    Aggregated billing figures for the platform overview (KPI tiles + charts), computed on demand.

                    **Response (`BillingSummaryResponse`):**
                    - `totalBilled`, `billsCount`, `averageBill` — over the `range` window.
                    - `byReason` — a map keyed `AUTO` and `MANUAL`, each `{ amount, count }`.
                    - `trend` — `{ interval, bucketLabels[], billed[], count[] }`; the three arrays are \
                      index-aligned with `bucketLabels`, oldest bucket first. Labels are `yyyy-MM-dd` (DAY), \
                      `yyyy-'W'ww` (WEEK), or `yyyy-MM` (MONTH).
                    - `topOrganizations` — highest-billed organizations within `range`, descending.
                    - `currency` and `refreshedAt` (when this rollup was computed).

                    Note: `trend` always spans the last `trendBuckets` intervals up to now, independent of `range`.
                    Only accessible by ROOT or ADMIN."""
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Summary computed successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = BillingSummaryResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid query parameter",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
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
    ResponseEntity<BillingSummaryResponse> getSummary(
            @Parameter(description = "Rolling window for the totals (defaults to ALL)")
            @RequestParam(defaultValue = "ALL") DashboardRange range,
            @Parameter(description = "Trend bucket width (defaults to MONTH)")
            @RequestParam(defaultValue = "MONTH") TrendInterval trendInterval,
            @Parameter(description = "Number of trend buckets; clamped to 1..90 (defaults to 12)", example = "12")
            @RequestParam(defaultValue = "12") int trendBuckets,
            @Parameter(description = "Number of top organizations; clamped to 1..20 (defaults to 5)", example = "5")
            @RequestParam(defaultValue = "5") int topOrgs);

    /**
     * Set a bill's payment state
     */
    @PatchMapping("/{billId}/payment-status")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN')")
    @Operation(
            summary = "Update a bill's payment status",
            description = """
                    Mark a bill `PAID` or `UNPAID` (request body: `{ "paymentStatus": "PAID" }`). Bills are \
                    created `UNPAID`; this is the only way the state changes — it is a manual admin action, \
                    not derived from any payment gateway. Returns the updated `BillResponse`. \
                    Only accessible by ROOT or ADMIN."""
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Payment status updated",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = BillResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid payment status",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Access forbidden - requires ROOT or ADMIN",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Bill not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<BillResponse> updatePaymentStatus(
            @PathVariable String billId,
            @Valid @RequestBody UpdatePaymentStatusRequest request);
}
