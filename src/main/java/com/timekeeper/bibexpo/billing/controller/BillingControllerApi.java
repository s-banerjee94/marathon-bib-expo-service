package com.timekeeper.bibexpo.billing.controller;

import com.timekeeper.bibexpo.exception.ErrorResponse;
import com.timekeeper.bibexpo.billing.model.dto.request.LineItemRequest;
import com.timekeeper.bibexpo.billing.model.dto.request.ParticipantLineRequest;
import com.timekeeper.bibexpo.billing.model.dto.response.BillGenerationResponse;
import com.timekeeper.bibexpo.billing.model.dto.response.BillResponse;
import com.timekeeper.bibexpo.billing.model.entity.InvoiceStatus;
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
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "Event Billing", description = "List an event's generated bills and request an on-demand bill")
@SecurityRequirement(name = "bearerAuth")
public interface BillingControllerApi {

    /**
     * List all bills generated for an event
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN')")
    @Operation(
            summary = "List an event's bills",
            description = """
                    Return every bill for the event, newest first — each with its full line items and \
                    recomputed totals (see BillResponse). Bills are produced by the billing pipeline: an \
                    automatic timer 24 hours after the event turns terminal (always a draft), or on-demand \
                    via the generate endpoint. Use each bill's `status` to tell drafts from finals. \
                    ROOT and ADMIN can view any event's bills; ORGANIZER_ADMIN only for their organization's events."""
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Bills retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = BillResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Access forbidden - user does not have permission",
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
    ResponseEntity<List<BillResponse>> listBills(
            @PathVariable Long eventId,
            @AuthenticationPrincipal User currentUser);

    /**
     * Request an on-demand bill for a terminal event
     */
    @PostMapping("/generate")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN')")
    @Operation(
            summary = "Generate a bill on demand",
            description = """
                    Ask the billing pipeline to generate a bill for a completed or cancelled event now, \
                    and get back the outcome plus the event's refreshed bill list.

                    `mode=DRAFT` (default) produces/refreshes the event's single replaceable proforma \
                    (no invoice number, editable, not payable). `mode=FINAL` issues the numbered, immutable \
                    tax invoice (`invoiceNumber` like `INV/2026-27/0001`) that closes the event to all \
                    further billing and blocks it from being reopened. Only ROOT and ADMIN may request \
                    FINAL; an ORGANIZER_ADMIN request is always treated as DRAFT.

                    A separate 24h timer also auto-generates a **draft** (never a final) and notifies admins \
                    to review it. **When ROOT or ADMIN makes any manual request (draft or final), that pending \
                    24h auto-draft timer is deleted immediately** — they have engaged, so the nudge is \
                    redundant. An ORGANIZER_ADMIN request leaves the timer running so platform admins are \
                    still notified at 24h.

                    On `mode=FINAL` the tax-invoice PDF is rendered and stored **synchronously**, so the \
                    returned FINAL bill's `downloadUrl` is ready to use right away (it is null only if PDF \
                    rendering failed — the bill is still final in that case, and the PDF can be regenerated).

                    Each manual request spends one of the caller's limited per-event slots, and no request is \
                    allowed once a final exists. The response `status` is one of `CREATED_DRAFT`, \
                    `CREATED_FINAL`, `SKIPPED_DUPLICATE`, `SKIPPED_NOT_BILLABLE`, `ALREADY_FINAL`. \
                    ROOT and ADMIN can bill any event; ORGANIZER_ADMIN only their own."""
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Request processed; the outcome and refreshed bill list are returned",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = BillGenerationResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Event is not yet completed or cancelled",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Access forbidden - user does not have permission",
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
                    responseCode = "502",
                    description = "The billing pipeline failed to generate the bill",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<BillGenerationResponse> generateBill(
            @PathVariable Long eventId,
            @Parameter(description = "DRAFT (default) for a replaceable proforma, or FINAL for the issued numbered tax invoice. "
                    + "FINAL is honored only for ROOT/ADMIN; an ORGANIZER_ADMIN request is always DRAFT.")
            @RequestParam(defaultValue = "DRAFT") InvoiceStatus mode,
            @AuthenticationPrincipal User currentUser);

    /**
     * Add a charge or adjustment line to a draft bill
     */
    @PostMapping("/invoices/{invoiceId}/line-items")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN')")
    @Operation(
            summary = "Add a line item to a draft bill",
            description = """
                    Add one charge or adjustment line to a DRAFT bill and get back the whole bill with \
                    its line items and recomputed totals.

                    **Body by `kind`:**
                    - **Charge** (`EXTRA_USER`, `SMS_CAMPAIGN`, `SURCHARGE`, `CUSTOM`): send `unitPrice` \
                    (required) and `quantity` (optional, defaults to 1). The line `amount` is computed as \
                    `quantity × unitPrice` — do **not** send `amount`. Example: 2 distributors at 200 → \
                    `{"kind":"EXTRA_USER","description":"Extra distributors","quantity":2,"unitPrice":200}` \
                    → amount 400.
                    - **Discount** (`DISCOUNT`): send **either** `amount` (a fixed value, stored as a negative \
                    line) **or** `percent` (1–100). A percentage discount applies to the pre-tax charge base \
                    (participant fee + all charges) and is **re-derived automatically every time the bill \
                    changes**, so it always tracks the current total. Sending both, or neither, is a 400.
                    - **`PARTICIPANT`** cannot be added here — it is created by the billing pipeline. Use the \
                    update endpoint to change its unit price/quantity.

                    Totals are always recomputed server-side; GST is applied on the net (post-discount) \
                    subtotal. Only ROOT and ADMIN may edit; only DRAFT bills are editable (a FINAL is frozen)."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Line added; the refreshed bill is returned",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = BillResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bill is final, the body is invalid for the kind (e.g. a charge with no unit price, a discount that is not exactly one of amount/percent), or a discount drives the total below zero",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden - user does not have permission",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Event or bill not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<BillResponse> addLineItem(
            @PathVariable Long eventId,
            @Parameter(description = "The bill's billId (UUID), as returned by the list/generate endpoints — not a numeric id",
                    example = "9f1c2e3a-4b5d-6e7f-8a9b-0c1d2e3f4a5b")
            @PathVariable String invoiceId,
            @Valid @RequestBody LineItemRequest request,
            @AuthenticationPrincipal User currentUser);

    /**
     * Update a charge or adjustment line on a draft bill
     */
    @PutMapping("/invoices/{invoiceId}/line-items/{lineItemId}")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN')")
    @Operation(
            summary = "Update a manual line item on a draft bill",
            description = """
                    Replace the values of one **manual** line on a DRAFT bill (a charge or a discount) and \
                    get back the whole bill with its line items. The body follows the same rules as the add \
                    endpoint (charge → `unitPrice` with optional `quantity`; discount → `amount` or `percent`).

                    The system-generated **PARTICIPANT** line is **not** edited here — use \
                    `PUT .../invoices/{invoiceId}/participant-line`. Other system-managed lines cannot be \
                    edited either.

                    Only ROOT and ADMIN may edit; only DRAFT bills are editable (a FINAL is frozen)."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Line updated; the refreshed bill is returned",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = BillResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bill is final, the body is invalid for the kind, or the line is the participant charge / system-managed",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden - user does not have permission",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Event, bill or line item not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<BillResponse> updateLineItem(
            @PathVariable Long eventId,
            @Parameter(description = "The bill's billId (UUID), as returned by the list/generate endpoints — not a numeric id",
                    example = "9f1c2e3a-4b5d-6e7f-8a9b-0c1d2e3f4a5b")
            @PathVariable String invoiceId,
            @Parameter(description = "The line item's numeric id, as returned in the bill's lineItems[].id", example = "12")
            @PathVariable Long lineItemId,
            @Valid @RequestBody LineItemRequest request,
            @AuthenticationPrincipal User currentUser);

    /**
     * Update the participant fee line on a draft bill
     */
    @PutMapping("/invoices/{invoiceId}/participant-line")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN')")
    @Operation(
            summary = "Update the participant fee line on a draft bill",
            description = """
                    Change the system-generated PARTICIPANT fee line's **unit price and/or quantity** on a \
                    DRAFT bill, and get back the whole bill. Send only the field(s) you want to change — an \
                    omitted field keeps its current value (so you can change just the unit price without \
                    touching the quantity). The line's type and description are system-managed; its `amount` \
                    recomputes as `quantity × unitPrice`.

                    Provide at least one of `unitPrice` / `quantity`. A later count refresh from the billing \
                    pipeline keeps your overridden unit price but resets the quantity to the real participant \
                    count. Only ROOT and ADMIN may edit; only DRAFT bills are editable (a FINAL is frozen)."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Participant line updated; the refreshed bill is returned",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = BillResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bill is final, or neither unitPrice nor quantity was provided",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden - user does not have permission",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Event, bill or participant line not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<BillResponse> updateParticipantLine(
            @PathVariable Long eventId,
            @Parameter(description = "The bill's billId (UUID), as returned by the list/generate endpoints — not a numeric id",
                    example = "9f1c2e3a-4b5d-6e7f-8a9b-0c1d2e3f4a5b")
            @PathVariable String invoiceId,
            @Valid @RequestBody ParticipantLineRequest request,
            @AuthenticationPrincipal User currentUser);

    /**
     * Remove a charge or adjustment line from a draft bill
     */
    @DeleteMapping("/invoices/{invoiceId}/line-items/{lineItemId}")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN')")
    @Operation(
            summary = "Remove a line item from a draft bill",
            description = """
                    Remove one line from a DRAFT bill and get back the whole bill with its recomputed \
                    totals. The PARTICIPANT line cannot be removed (it is the core charge), and other \
                    system-managed lines cannot be removed. A percentage discount on the bill re-derives \
                    automatically after the removal. Only ROOT and ADMIN may edit; only DRAFT bills are \
                    editable (a FINAL is frozen)."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Line removed; the refreshed bill is returned",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = BillResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bill is final, or the line is the participant charge / system-managed",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden - user does not have permission",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Event, bill or line item not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<BillResponse> deleteLineItem(
            @PathVariable Long eventId,
            @Parameter(description = "The bill's billId (UUID), as returned by the list/generate endpoints — not a numeric id",
                    example = "9f1c2e3a-4b5d-6e7f-8a9b-0c1d2e3f4a5b")
            @PathVariable String invoiceId,
            @Parameter(description = "The line item's numeric id, as returned in the bill's lineItems[].id", example = "12")
            @PathVariable Long lineItemId,
            @AuthenticationPrincipal User currentUser);
}
