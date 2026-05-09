package com.timekeeper.bibexpo.controller;

import com.timekeeper.bibexpo.exception.ErrorResponse;
import com.timekeeper.bibexpo.model.dto.request.CreateSmsCampaignRequest;
import com.timekeeper.bibexpo.model.dto.request.UpdateSmsCampaignRequest;
import com.timekeeper.bibexpo.model.dto.response.PageableResponse;
import com.timekeeper.bibexpo.model.dto.response.SmsCampaignResponse;
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
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "SMS Campaign Management", description = "APIs for managing SMS campaigns for marathon events")
@SecurityRequirement(name = "bearerAuth")
public interface SmsCampaignControllerApi {

    /**
     * Create a new SMS campaign for an event
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @Operation(
            summary = "Create a new SMS campaign",
            description = """
                    Create a new SMS campaign for an event. \
                    AUTO_BIB_COLLECTED campaigns start as ACTIVE immediately and fire per participant on bib collection. \
                    SCHEDULED and MANUAL campaigns start as DRAFT and must be activated separately. \
                    scheduledAt is required for SCHEDULED type and ignored for all others."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Campaign created successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = SmsCampaignResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request or validation error",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Event or SMS template not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "An active AUTO_BIB_COLLECTED campaign already exists for this event",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<SmsCampaignResponse> createCampaign(
            @Parameter(description = "Event ID", example = "1") @PathVariable Long eventId,
            @Valid @RequestBody CreateSmsCampaignRequest request,
            @AuthenticationPrincipal User currentUser);

    /**
     * Update an existing SMS campaign (DRAFT or ACTIVE only)
     */
    @PatchMapping("/{campaignId}")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @Operation(
            summary = "Update an SMS campaign",
            description = """
                    Update a DRAFT or ACTIVE campaign. SENT campaigns cannot be edited. \
                    Changing triggerType automatically adjusts status: to AUTO_BIB_COLLECTED sets ACTIVE, to SCHEDULED or MANUAL sets DRAFT. \
                    scheduledAt is applied only when triggerType is SCHEDULED; it is cleared for all other types."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Campaign updated successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = SmsCampaignResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request or campaign cannot be edited",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Campaign or SMS template not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<SmsCampaignResponse> updateCampaign(
            @Parameter(description = "Event ID", example = "1") @PathVariable Long eventId,
            @Parameter(description = "Campaign ID", example = "1") @PathVariable Long campaignId,
            @Valid @RequestBody UpdateSmsCampaignRequest request,
            @AuthenticationPrincipal User currentUser);

    /**
     * Get all SMS campaigns for an event (paginated)
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @Operation(summary = "Get all SMS campaigns for an event")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Campaigns retrieved successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = PageableResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Event not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<PageableResponse<SmsCampaignResponse>> getCampaignsByEvent(
            @Parameter(description = "Event ID", example = "1") @PathVariable Long eventId,
            @Parameter(description = "Pagination and sorting parameters") Pageable pageable,
            @AuthenticationPrincipal User currentUser);

    /**
     * Get an SMS campaign by ID
     */
    @GetMapping("/{campaignId}")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @Operation(summary = "Get an SMS campaign by ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Campaign retrieved successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = SmsCampaignResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Campaign not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<SmsCampaignResponse> getCampaignById(
            @Parameter(description = "Event ID", example = "1") @PathVariable Long eventId,
            @Parameter(description = "Campaign ID", example = "1") @PathVariable Long campaignId,
            @AuthenticationPrincipal User currentUser);

    /**
     * Deactivate an ACTIVE campaign, moving it back to DRAFT
     */
    @PatchMapping("/{campaignId}/deactivate")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @Operation(summary = "Deactivate a campaign", description = "Moves an ACTIVE campaign back to DRAFT. Only ACTIVE campaigns can be deactivated.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Campaign deactivated successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = SmsCampaignResponse.class))),
            @ApiResponse(responseCode = "400", description = "Campaign cannot be deactivated (not in ACTIVE status)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Campaign not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<SmsCampaignResponse> deactivateCampaign(
            @Parameter(description = "Event ID", example = "1") @PathVariable Long eventId,
            @Parameter(description = "Campaign ID", example = "1") @PathVariable Long campaignId,
            @AuthenticationPrincipal User currentUser);

    /**
     * Delete a DRAFT or CANCELLED campaign
     */
    @DeleteMapping("/{campaignId}")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @Operation(summary = "Delete a campaign", description = "Only DRAFT or CANCELLED campaigns can be deleted.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Campaign deleted successfully"),
            @ApiResponse(responseCode = "400", description = "Campaign cannot be deleted (only DRAFT campaigns can be deleted)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Campaign not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<Void> deleteCampaign(
            @Parameter(description = "Event ID", example = "1") @PathVariable Long eventId,
            @Parameter(description = "Campaign ID", example = "1") @PathVariable Long campaignId,
            @AuthenticationPrincipal User currentUser);
}
