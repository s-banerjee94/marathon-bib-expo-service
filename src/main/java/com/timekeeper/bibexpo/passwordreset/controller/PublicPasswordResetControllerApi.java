package com.timekeeper.bibexpo.passwordreset.controller;

import com.timekeeper.bibexpo.exception.ErrorResponse;
import com.timekeeper.bibexpo.passwordreset.model.dto.request.CompletePasswordResetRequest;
import com.timekeeper.bibexpo.passwordreset.model.dto.request.ForgotPasswordRequest;
import com.timekeeper.bibexpo.passwordreset.model.dto.response.PasswordResetTokenStatusResponse;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Public API for the forgot-password flow. No authentication required — a valid reset token is the
 * credential. Requesting a link never discloses whether an account exists.
 */
@Tag(name = "Password Reset (Public)", description = "Request and complete a password reset — no authentication required")
@RequestMapping("/api/auth/password-reset")
public interface PublicPasswordResetControllerApi {

    @Operation(
            summary = "Request a password-reset link",
            description = "Sends a reset link to the account's own registered phone if the identifier "
                    + "(username, email, or phone) matches an eligible account. Always returns the same "
                    + "generic acknowledgement, so it never reveals whether an account exists."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Request accepted (whether or not an account matched)")
    })
    @PostMapping("/forgot")
    ResponseEntity<Void> forgotPassword(
            @Parameter(description = "Account identifier: username, email, or phone", required = true)
            @Valid @RequestBody ForgotPasswordRequest request
    );

    @Operation(
            summary = "Validate a reset token",
            description = "Returns the masked username and expiry for a valid token so the set-new-password "
                    + "form can render. No authentication required."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token is valid",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = PasswordResetTokenStatusResponse.class))),
            @ApiResponse(responseCode = "404", description = "Token is invalid or has expired",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{token}")
    ResponseEntity<PasswordResetTokenStatusResponse> validateToken(
            @Parameter(description = "Reset token", required = true)
            @PathVariable String token
    );

    @Operation(
            summary = "Set a new password using a reset token",
            description = "Consumes the token and sets the new password. The token cannot be reused, and any "
                    + "active session for the account is ended. No authentication required."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Password set successfully"),
            @ApiResponse(responseCode = "400", description = "New password invalid",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Token is invalid or has expired",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/{token}")
    ResponseEntity<Void> completeReset(
            @Parameter(description = "Reset token", required = true)
            @PathVariable String token,

            @Parameter(description = "The new password", required = true)
            @Valid @RequestBody CompletePasswordResetRequest request
    );
}
