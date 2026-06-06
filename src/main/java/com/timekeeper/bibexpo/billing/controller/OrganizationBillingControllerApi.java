package com.timekeeper.bibexpo.billing.controller;

import com.timekeeper.bibexpo.exception.ErrorResponse;
import com.timekeeper.bibexpo.billing.model.dto.response.OrganizationBillingResponse;
import com.timekeeper.bibexpo.model.entity.User;
import io.swagger.v3.oas.annotations.Operation;
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

@Tag(name = "Billing Administration",
        description = "Organization and platform billing rollups, the global bill feed, and payment-state management")
@SecurityRequirement(name = "bearerAuth")
public interface OrganizationBillingControllerApi {

    /**
     * List all bills for an organization (rollup across its events)
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN')")
    @Operation(
            summary = "List an organization's bills",
            description = """
                    Return every bill generated across all of an organization's events.

                    **Response (`OrganizationBillingResponse`):** `organizationId`, `currency`, \
                    `totalBilled` (sum of `totalAmount` across the bills), and `bills[]` — an array of \
                    `BillResponse` objects, newest first, each with a short-lived presigned PDF \
                    `downloadUrl`.

                    ROOT and ADMIN can view any organization; ORGANIZER_ADMIN only their own \
                    (`403` for any other organization)."""
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Bills retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = OrganizationBillingResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Access forbidden - not the caller's organization or wrong role",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Organization not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<OrganizationBillingResponse> listOrganizationBills(
            @PathVariable Long organizationId,
            @AuthenticationPrincipal User currentUser);
}
