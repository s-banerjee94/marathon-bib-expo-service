package com.timekeeper.bibexpo.controller;

import com.timekeeper.bibexpo.exception.ErrorResponse;
import com.timekeeper.bibexpo.model.dto.request.CreateSmsCampaignRequest;
import com.timekeeper.bibexpo.model.dto.request.UpdateSmsCampaignRequest;
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
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "SMS Campaign Management", description = "APIs for managing SMS campaigns for marathon events")
@SecurityRequirement(name = "bearerAuth")
public interface SmsCampaignControllerApi {

    /**
     * Create a new campaign — DRAFT or fully armed depending on whether triggerType is provided
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @Operation(
            summary = "Create a new SMS campaign",
            description = """
                    Create a new SMS campaign. \
                    If triggerType is omitted the campaign is saved as DRAFT for future use. \
                    If triggerType is present the campaign is armed and moves directly to ACTIVE. \
                    targetFilter is required when triggerType is present. \
                    scheduledDate and scheduledTime are required when triggerType is SCHEDULED and must be at least 3 minutes in the future (resolved using the event timezone). \
                    Only one AUTO_BIB_COLLECTED campaign can be ACTIVE per event. \
                    An event can have a maximum of 20 campaigns."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Campaign created successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = SmsCampaignResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request, validation failed, schedule too soon, or event has reached the 20 campaign limit",
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
     * Update a DRAFT campaign — update fields and optionally arm in one request
     */
    @PatchMapping("/{campaignId}")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @Operation(
            summary = "Update a DRAFT campaign",
            description = """
                    Update a DRAFT campaign. Only DRAFT campaigns can be modified — disarm first if the campaign is ACTIVE. \
                    name and smsTemplateId are optional and updated only when present. \
                    If triggerType is included the campaign is armed and moves to ACTIVE in the same request. \
                    targetFilter is required when triggerType is present. \
                    scheduledDate and scheduledTime are required when triggerType is SCHEDULED and must be at least 3 minutes in the future (resolved using the event timezone). \
                    Only one AUTO_BIB_COLLECTED campaign can be ACTIVE per event."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Campaign updated successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = SmsCampaignResponse.class))),
            @ApiResponse(responseCode = "400", description = "Campaign is not DRAFT, validation failed, or schedule is too soon",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Campaign or SMS template not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "An active AUTO_BIB_COLLECTED campaign already exists for this event",
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
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = SmsCampaignResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Event not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<List<SmsCampaignResponse>> getCampaignsByEvent(
            @Parameter(description = "Event ID", example = "1") @PathVariable Long eventId,
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
     * Disarm an ACTIVE campaign — clears trigger config and moves back to DRAFT
     */
    @PatchMapping("/{campaignId}/disarm")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @Operation(
            summary = "Disarm a campaign",
            description = "Clears trigger type, target filter, and scheduled time and moves the campaign back to DRAFT. SCHEDULED campaigns cannot be disarmed within 30 seconds of the scheduled time."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Campaign disarmed successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = SmsCampaignResponse.class))),
            @ApiResponse(responseCode = "400", description = "Campaign is not ACTIVE or within the 30-second disarm cutoff",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Campaign not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<SmsCampaignResponse> disarmCampaign(
            @Parameter(description = "Event ID", example = "1") @PathVariable Long eventId,
            @Parameter(description = "Campaign ID", example = "1") @PathVariable Long campaignId,
            @AuthenticationPrincipal User currentUser);

    /**
     * Delete a DRAFT campaign permanently
     */
    @DeleteMapping("/{campaignId}")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @Operation(summary = "Delete a campaign", description = "Only DRAFT campaigns can be deleted.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Campaign deleted successfully"),
            @ApiResponse(responseCode = "400", description = "Campaign is not in DRAFT status",
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
