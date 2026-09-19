package com.timekeeper.bibexpo.inventory.controller;

import com.timekeeper.bibexpo.inventory.model.dto.request.AdjustStockRequest;
import com.timekeeper.bibexpo.inventory.model.dto.request.ReceiveStockRequest;
import com.timekeeper.bibexpo.inventory.model.dto.request.TransferStockRequest;
import com.timekeeper.bibexpo.inventory.model.dto.response.StockBalanceResponse;
import com.timekeeper.bibexpo.inventory.model.dto.response.StockItemResponse;
import com.timekeeper.bibexpo.inventory.model.dto.response.StockMovementResponse;
import com.timekeeper.bibexpo.inventory.model.enums.MovementReason;
import com.timekeeper.bibexpo.inventory.model.enums.MovementType;
import com.timekeeper.bibexpo.shared.error.ErrorResponse;
import com.timekeeper.bibexpo.shared.web.PageableResponse;
import com.timekeeper.bibexpo.user.model.entity.User;
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
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Instant;
import java.util.List;

@Tag(name = "Inventory Stock", description = "Balances and the append-only ledger behind them")
@SecurityRequirement(name = "bearerAuth")
public interface InventoryStockControllerApi {

    @Operation(
            summary = "List stock",
            description = """
                    One page of what the organization has on hand, one entry per item, carrying the \
                    variant-and-location rows behind its total.

                    Every filter is optional and they combine, and filtering happens in the \
                    database. The page counts items rather than balance rows, so an item is never \
                    split across two pages and the page totals describe the filtered set.

                    A search or a location narrows the rows under each item too, and the item's \
                    total then counts only the rows still shown. An item holding no stock at all \
                    still appears, with an empty row list, unless a location filter rules it out.

                    In item-name order unless a <code>sort</code> is given, which replaces it."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Stock retrieved successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = PageableResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Organization not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/stock")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    ResponseEntity<PageableResponse<StockItemResponse>> listStock(
            @PathVariable Long organizationId,

            @Parameter(description = "Keep only entries this text appears in — an item name, one of a variant's values, or a location name. Matched anywhere, ignoring case.",
                    example = "bottle")
            @RequestParam(required = false) String search,

            @Parameter(description = "Keep only stock sitting at this location.", example = "9")
            @RequestParam(required = false) Long locationId,

            @Parameter(description = "Keep only items that have run low. An item with no threshold of its own counts as low once the shelf is empty.",
                    example = "true")
            @RequestParam(required = false) Boolean lowOnly,

            @Parameter(description = "Pagination parameters") Pageable pageable,
            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser);

    @Operation(
            summary = "List ledger movements",
            description = """
                    One page of the organization's append-only stock ledger.

                    Every filter is optional and they combine. Filtering happens in the database, so \
                    the page totals describe the filtered set, not the whole ledger.

                    Newest first — by <code>occurredAt</code> descending, the moment the stock \
                    actually moved rather than the moment the row was saved — unless a \
                    <code>sort</code> is given, which replaces it."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Movements retrieved successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = PageableResponse.class))),
            @ApiResponse(responseCode = "400", description = "The date range starts after it ends",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Organization not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/movements")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    ResponseEntity<PageableResponse<StockMovementResponse>> listMovements(
            @PathVariable Long organizationId,

            @Parameter(description = "Keep only lines for this item, across every one of its variants.",
                    example = "22")
            @RequestParam(required = false) Long itemId,

            @Parameter(description = "Keep only lines for this one variant.", example = "41")
            @RequestParam(required = false) Long variantId,

            @Parameter(description = "Keep only this kind of movement.", example = "TRANSFER")
            @RequestParam(required = false) MovementType type,

            @Parameter(description = "Keep only lines posted for this reason. Most lines carry none.",
                    example = "DAMAGED")
            @RequestParam(required = false) MovementReason reason,

            @Parameter(description = "Keep only lines posted by this username, matched whole.",
                    example = "organizer1")
            @RequestParam(required = false) String performedBy,

            @Parameter(description = "Keep only lines that occurred on or after this instant, ISO-8601.",
                    example = "2026-09-01T00:00:00Z")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant occurredFrom,

            @Parameter(description = "Keep only lines that occurred on or before this instant, ISO-8601. Must not be before <code>occurredFrom</code>.",
                    example = "2026-09-30T23:59:59Z")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant occurredTo,

            @Parameter(description = "Pagination parameters") Pageable pageable,
            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser);

    @Operation(
            summary = "Receive stock",
            description = """
                    Records stock arriving at a location from outside the system — an opening balance or a \
                    manual receipt. Always succeeds; a receipt can never be short. The variant and the
                    location must both belong to this organization."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Stock received successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = StockBalanceResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request body",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Organization, variant, or location not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/receipts")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    ResponseEntity<StockBalanceResponse> receiveStock(
            @PathVariable Long organizationId,
            @Valid @RequestBody ReceiveStockRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser);

    @Operation(
            summary = "Transfer stock between locations",
            description = """
                    Moves stock from one location to another. Posts two ledger lines, one per location, and \
                    returns the resulting balance at each — source first, then destination. Answers 409
                    when the source location does not hold enough stock."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Stock transferred successfully",
                    content = @Content(mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = StockBalanceResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Invalid request body",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Organization, variant, or location not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Not enough stock at the source location",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/transfers")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    ResponseEntity<List<StockBalanceResponse>> transferStock(
            @PathVariable Long organizationId,
            @Valid @RequestBody TransferStockRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser);

    @Operation(
            summary = "Adjust a counted quantity",
            description = """
                    Corrects the counted quantity at a location. The reason must say whether the stock was
                    damaged, lost, or simply miscounted. DAMAGED and LOST always remove stock, whatever sign the
                    quantity carries; CORRECTION adds or removes according to the sign. An adjustment can never
                    take the balance below zero."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Stock adjusted successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = StockBalanceResponse.class))),
            @ApiResponse(responseCode = "400", description = "Zero quantity, or a reason that does not explain a recount",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Organization, variant, or location not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "The adjustment would take the balance below zero",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/adjustments")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    ResponseEntity<StockBalanceResponse> adjustStock(
            @PathVariable Long organizationId,
            @Valid @RequestBody AdjustStockRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser);
}
