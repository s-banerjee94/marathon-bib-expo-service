package com.timekeeper.bibexpo.invitation.controller;

import com.timekeeper.bibexpo.exception.ErrorResponse;
import com.timekeeper.bibexpo.invitation.model.dto.request.CreateInvitationRequest;
import com.timekeeper.bibexpo.invitation.model.dto.response.InvitationLinkResponse;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * API interface for issuing user-invite links. The link's role and organization are fixed
 * here and validated against the issuer's authority, mirroring direct user creation.
 */
@Tag(name = "User Invitations", description = "Issue one-time invite links to create users")
@RequestMapping("/api/users/invitations")
@SecurityRequirement(name = "bearerAuth")
public interface InvitationControllerApi {

    @Operation(
            summary = "Issue a user-invite link",
            description = """
                    Issues a one-time, short-lived link the invitee opens to create their own account. \
                    The role and organization are fixed in the link and cannot be changed by the invitee. \
                    The issuer must be allowed to create that role: \
                    ROOT can invite ADMIN, ORGANIZER_ADMIN, ORGANIZER_USER, DISTRIBUTOR (any organization). \
                    ADMIN can invite ORGANIZER_ADMIN, ORGANIZER_USER, DISTRIBUTOR (any organization). \
                    ORGANIZER_ADMIN can invite ORGANIZER_USER, DISTRIBUTOR (own organization only). \
                    ORGANIZER_USER can invite DISTRIBUTOR (own organization only). \
                    A DISTRIBUTOR invite additionally requires an eventId; the event must belong to the same \
                    organization and must not be completed or cancelled, and is fixed in the link too."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Invite link issued",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = InvitationLinkResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid role, organization required but missing or disabled, "
                    + "missing event for a distributor, or the event is completed or cancelled",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Issuer not allowed to create the requested role or organization",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Organization not found, "
                    + "or event not found / outside the organization (for a distributor)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    ResponseEntity<InvitationLinkResponse> createInvitation(
            @Parameter(description = "Role, organization, and (for a distributor) event the invite is fixed to", required = true)
            @Valid @RequestBody CreateInvitationRequest request,

            @Parameter(hidden = true)
            @AuthenticationPrincipal User currentUser
    );
}
