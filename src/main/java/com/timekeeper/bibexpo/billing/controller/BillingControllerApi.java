package com.timekeeper.bibexpo.billing.controller;

import com.timekeeper.bibexpo.exception.ErrorResponse;
import com.timekeeper.bibexpo.billing.model.dto.response.BillGenerationResponse;
import com.timekeeper.bibexpo.billing.model.dto.response.BillResponse;
import com.timekeeper.bibexpo.model.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

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
                    Return every bill generated for the event, newest first, each with a short-lived \
                    presigned URL to download its PDF. Bills are produced by the billing pipeline \
                    (an automatic timer 5 hours after the event turns terminal, or on-demand). \
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
                    Ask the billing pipeline to generate a bill for a completed or cancelled event now. \
                    The same dedup-by-count rule as the automatic timer applies, so a redundant request \
                    returns the existing bill instead of a duplicate. Reopening an event, adding more \
                    participants, and re-completing produces a fresh bill. \
                    ROOT and ADMIN can bill any event; ORGANIZER_ADMIN only for their organization's events."""
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
            @AuthenticationPrincipal User currentUser);
}
