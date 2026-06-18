package com.timekeeper.bibexpo.invitation.controller;

import com.timekeeper.bibexpo.exception.ErrorResponse;
import com.timekeeper.bibexpo.invitation.model.dto.request.AcceptInvitationRequest;
import com.timekeeper.bibexpo.invitation.model.dto.response.InvitationDetailsResponse;
import com.timekeeper.bibexpo.model.dto.response.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Public API for opening and accepting invite links. No authentication required — the token
 * itself is the credential, and the fixed role/organization come from the stored invitation.
 */
@Tag(name = "User Invitations (Public)", description = "Open and accept invite links — no authentication required")
@RequestMapping("/api/auth/invitations")
public interface PublicInvitationControllerApi {

    @Operation(
            summary = "Read a pending invite",
            description = "Returns the fixed role and organization of the invite (plus the event, for a distributor invite) "
                    + "so the accept form can render. No authentication required."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Invite details",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = InvitationDetailsResponse.class))),
            @ApiResponse(responseCode = "404", description = "Invite is invalid or has expired",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{token}")
    ResponseEntity<InvitationDetailsResponse> getInvitation(
            @Parameter(description = "Invite token", required = true)
            @PathVariable String token
    );

    @Operation(
            summary = "Accept an invite and create the account",
            description = """
                    Creates the account using the invitee's details together with the invite's fixed \
                    role and organization (and event, for a distributor invite). Any role, organization, or \
                    event in the body is ignored. The link is \
                    consumed only on success, so a recoverable failure (such as a taken username) can be \
                    retried while the link is still live. No authentication required."""
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Account created",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid details, or required details missing, or limit exceeded",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Invite is invalid or has expired",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Username, email, or phone already exists",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/{token}")
    ResponseEntity<UserResponse> acceptInvitation(
            @Parameter(description = "Invite token", required = true)
            @PathVariable String token,

            @Parameter(description = "Invitee's account details", required = true)
            @Valid @RequestBody AcceptInvitationRequest request
    );
}
