package com.timekeeper.bibexpo.passwordreset.controller;

import com.timekeeper.bibexpo.exception.ErrorResponse;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.passwordreset.model.dto.request.IssueResetLinkRequest;
import com.timekeeper.bibexpo.passwordreset.model.dto.response.PasswordResetLinkResponse;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * API interface for administrator-initiated password resets. The issuer must be allowed to manage
 * the target user (same permission hierarchy as updating them). The link is returned so it can be
 * shared, and is additionally delivered to the user's own registered phone over any requested
 * channels.
 */
@Tag(name = "Password Reset (Admin)", description = "Issue a password-reset link for a user")
@RequestMapping("/api/users")
@SecurityRequirement(name = "bearerAuth")
public interface PasswordResetAdminControllerApi {

    @Operation(
            summary = "Issue a password-reset link for a user",
            description = """
                    Issues a one-time, short-lived link the user opens to set a new password. The caller \
                    must be allowed to manage the target user (ROOT/ADMIN any user; ORGANIZER_ADMIN and \
                    ORGANIZER_USER only within their organization and only lower-privileged users). The \
                    link is always returned so the administrator can share it; if delivery channels are \
                    supplied it is also sent to the user's own registered phone. The administrator who \
                    issued the link is recorded in the audit trail."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Reset link issued",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = PasswordResetLinkResponse.class))),
            @ApiResponse(responseCode = "400", description = "Caller targeted their own account (use change/forgot password instead)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller not allowed to manage the target user",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{userId}/password-reset-link")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    ResponseEntity<PasswordResetLinkResponse> issueResetLink(
            @Parameter(description = "User the reset link is for", required = true)
            @PathVariable Long userId,

            @Parameter(description = "Optional channels to also deliver the link on")
            @Valid @RequestBody(required = false) IssueResetLinkRequest request,

            @Parameter(hidden = true)
            @AuthenticationPrincipal User currentUser
    );
}
