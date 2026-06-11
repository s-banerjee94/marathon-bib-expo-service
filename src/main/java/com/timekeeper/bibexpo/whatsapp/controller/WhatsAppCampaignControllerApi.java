package com.timekeeper.bibexpo.whatsapp.controller;

import com.timekeeper.bibexpo.exception.ErrorResponse;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.whatsapp.model.dto.request.CreateWhatsAppCampaignRequest;
import com.timekeeper.bibexpo.whatsapp.model.dto.request.UpdateWhatsAppCampaignRequest;
import com.timekeeper.bibexpo.whatsapp.model.dto.response.WhatsAppCampaignResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "WhatsApp Campaign Management",
        description = "WhatsApp campaigns per event: bib-collected auto-trigger or scheduled batch, dispatched through the organization's resolved sender")
@SecurityRequirement(name = "bearerAuth")
public interface WhatsAppCampaignControllerApi {

    /**
     * Create a WhatsApp campaign (DRAFT, or armed directly when triggerType is present).
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @Operation(
            summary = "Create a WhatsApp campaign",
            description = """
                    Creates a WhatsApp campaign for an event. Without `triggerType` the campaign is \
                    saved as DRAFT; with it the campaign is armed immediately (ACTIVE). Arming \
                    validates that the template's sender scope matches the organization's currently \
                    resolved WhatsApp sender — a template registered under the organization's own \
                    account cannot be armed while the organization is on the default sender, and \
                    vice versa. One active AUTO_BIB_COLLECTED campaign per event; an event can have \
                    a maximum of 20 WhatsApp campaigns."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Campaign created",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = WhatsAppCampaignResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error, campaign limit, schedule error, or template scope mismatch",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Event or template not found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "An active bib collection campaign already exists",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<WhatsAppCampaignResponse> createCampaign(
            @PathVariable Long eventId,
            @Valid @RequestBody CreateWhatsAppCampaignRequest request,
            @AuthenticationPrincipal User currentUser);

    /**
     * Update a DRAFT campaign (optionally arming it).
     */
    @PatchMapping("/{campaignId}")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @Operation(
            summary = "Update a WhatsApp campaign",
            description = "Updates a DRAFT campaign. If `triggerType` is present the campaign is armed (same validations as create). Non-draft campaigns must be disarmed first."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Campaign updated",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = WhatsAppCampaignResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error or campaign not in DRAFT",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Event, campaign or template not found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<WhatsAppCampaignResponse> updateCampaign(
            @PathVariable Long eventId,
            @PathVariable Long campaignId,
            @Valid @RequestBody UpdateWhatsAppCampaignRequest request,
            @AuthenticationPrincipal User currentUser);

    /**
     * List an event's WhatsApp campaigns.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @Operation(summary = "List WhatsApp campaigns")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Campaigns retrieved"),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Event not found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<List<WhatsAppCampaignResponse>> getCampaignsByEvent(
            @PathVariable Long eventId,
            @AuthenticationPrincipal User currentUser);

    /**
     * Fetch a single WhatsApp campaign.
     */
    @GetMapping("/{campaignId}")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @Operation(summary = "Get a WhatsApp campaign by ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Campaign retrieved",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = WhatsAppCampaignResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Event or campaign not found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<WhatsAppCampaignResponse> getCampaignById(
            @PathVariable Long eventId,
            @PathVariable Long campaignId,
            @AuthenticationPrincipal User currentUser);

    /**
     * Disarm an ACTIVE campaign back to DRAFT.
     */
    @PatchMapping("/{campaignId}/disarm")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @Operation(
            summary = "Disarm a WhatsApp campaign",
            description = "Moves an ACTIVE campaign back to DRAFT and clears its trigger, filter and schedule. Scheduled campaigns cannot be disarmed within 30 seconds of the scheduled time."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Campaign disarmed",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = WhatsAppCampaignResponse.class))),
            @ApiResponse(responseCode = "400", description = "Campaign not ACTIVE or inside the disarm cutoff",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Event or campaign not found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<WhatsAppCampaignResponse> disarmCampaign(
            @PathVariable Long eventId,
            @PathVariable Long campaignId,
            @AuthenticationPrincipal User currentUser);

    /**
     * Delete a DRAFT campaign.
     */
    @DeleteMapping("/{campaignId}")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @Operation(summary = "Delete a WhatsApp campaign", description = "Only DRAFT campaigns can be deleted.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Campaign deleted"),
            @ApiResponse(responseCode = "400", description = "Campaign not in DRAFT",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Event or campaign not found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<Void> deleteCampaign(
            @PathVariable Long eventId,
            @PathVariable Long campaignId,
            @AuthenticationPrincipal User currentUser);
}
