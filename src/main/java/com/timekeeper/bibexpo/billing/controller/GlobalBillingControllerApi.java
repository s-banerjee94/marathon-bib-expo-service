package com.timekeeper.bibexpo.billing.controller;

import com.timekeeper.bibexpo.exception.ErrorResponse;
import com.timekeeper.bibexpo.billing.model.dto.request.UpdatePaymentStatusRequest;
import com.timekeeper.bibexpo.billing.model.dto.response.BillResponse;
import com.timekeeper.bibexpo.model.dto.response.PageableResponse;
import com.timekeeper.bibexpo.billing.model.dto.response.BillStatsRefreshResponse;
import com.timekeeper.bibexpo.billing.model.dto.response.BillStatsResponse;
import com.timekeeper.bibexpo.billing.model.entity.PaymentStatus;
import com.timekeeper.bibexpo.model.enums.DashboardRange;
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
import org.springframework.web.bind.annotation.PostMapping;
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
                    `createdAt,desc`. Sortable fields are stored invoice columns such as \
                    `createdAt`, `totalAmount`, `status`, `paymentStatus`.

                    **Filters** (all optional, AND-combined): `organizationId`, `eventId`, \
                    `from`/`to` (inclusive range on `createdAt`), `reason`, `paymentStatus`, and \
                    `q` (case-insensitive substring of organizer name or event name).

                    The feed lists **both drafts and final bills** — use each item's `status` to tell \
                    them apart. Only accessible by ROOT or ADMIN."""
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
     * Auditor-grade platform billing statistics for KPI tiles and charts
     */
    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN')")
    @Operation(
            summary = "Global bill statistics",
            description = """
                    Auditor-grade billing statistics for the platform overview, served from a snapshot that is \
                    computed **entirely in a dedicated Lambda** (never in Spring) and recomputed asynchronously \
                    whenever a bill is finalized, marked paid/unpaid, or the snapshot is manually refreshed.

                    **Only issued (FINAL) bills count** — drafts are never included. Within a `range`, the FINAL \
                    bills finalized in the window form a cohort that reconciles: \
                    `billed = collected + outstanding` (both amount and count).

                    **Response (`BillStatsResponse`)** — the slice for the requested `range`:
                    - `billed` — `{ amount (gross), net (taxable value), tax (GST), count }`.
                    - `collected` — `{ amount, count }` of the cohort that is PAID.
                    - `outstanding` — `{ amount, count }` of the cohort still UNPAID (open receivable).
                    - `collectionRate` — `collected.amount / billed.amount * 100`.
                    - `averageBill`, `dso` (days sales outstanding — average age in days of unpaid bills).
                    - `gst` — `{ collected, outstanding, total }` GST liability split.
                    - `byReason` — billed `{ amount, count }` keyed `AUTO` and `MANUAL`.
                    - `aging` — outstanding split into `0-30 / 31-60 / 61-90 / 90+` day bands by finalize date.
                    - `trend` — billed-vs-collected over the last 12 months (`bucketLabels`, `billed`, \
                      `collected`, `count` index-aligned, oldest first); **range-independent**.
                    - `topOrganizations` — highest-billed organizations in the window, with billed/collected/outstanding.
                    - `currency`, `range`, `refreshedAt`, `computedBy`.

                    If the snapshot has never been computed, returns `200` with all figures zeroed and \
                    `refreshedAt: null` — the UI should show an empty state and offer a refresh. \
                    Only accessible by ROOT or ADMIN."""
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Statistics returned (zeroed with refreshedAt=null if never computed)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = BillStatsResponse.class)
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
    ResponseEntity<BillStatsResponse> getStats(
            @Parameter(description = "Range window for the figures (defaults to ALL)")
            @RequestParam(defaultValue = "ALL") DashboardRange range);

    /**
     * Manually trigger a recompute of the bill-stats snapshot
     */
    @PostMapping("/stats/refresh")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN')")
    @Operation(
            summary = "Refresh bill statistics",
            description = """
                    Asynchronously trigger the bill-stats Lambda to recompute the snapshot. Use this when an \
                    automatic recompute (on finalize or payment toggle) was missed — Spring never recomputes \
                    the figures itself.

                    Returns `202 Accepted` immediately with the snapshot's **current** (pre-refresh) \
                    `refreshedAt` so the UI can show an "updating…" state; the new figures land shortly after, \
                    so re-read `GET /api/billing/stats` a moment later to pick them up. The recompute is \
                    best-effort — if the Lambda is unavailable the previous snapshot remains in place. \
                    Only accessible by ROOT or ADMIN."""
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "202",
                    description = "Recompute triggered",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = BillStatsRefreshResponse.class)
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
    ResponseEntity<BillStatsRefreshResponse> refreshStats();

    /**
     * Set a bill's payment state
     */
    @PatchMapping("/{billId}/payment-status")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN')")
    @Operation(
            summary = "Mark a bill paid",
            description = """
                    Mark a bill `PAID` (request body: `{ "paymentStatus": "PAID" }`) — a manual admin action, \
                    not derived from any payment gateway. The bill is addressed by its `billId` (UUID).

                    **Payment is one-way.** Bills are created `UNPAID`; once a bill is marked `PAID` it is \
                    settled for good and **cannot be reverted to `UNPAID`** (such a request returns 400). \
                    The `UNPAID`→`PAID` transition stamps the collection date and triggers an asynchronous \
                    bill-stats recompute; a request that does not change the state is a no-op.

                    **Only FINAL bills are payable** — calling this on a DRAFT returns 400. Returns the \
                    updated `BillResponse`. Only accessible by ROOT or ADMIN."""
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Payment status updated (or unchanged for a no-op request)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = BillResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "The bill is a draft (drafts cannot be paid), or it is already paid and a revert to unpaid was requested",
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
            @Parameter(description = "The bill's billId (UUID), as returned in BillResponse.billId",
                    example = "9f1c2e3a-4b5d-6e7f-8a9b-0c1d2e3f4a5b")
            @PathVariable String billId,
            @Valid @RequestBody UpdatePaymentStatusRequest request);
}
