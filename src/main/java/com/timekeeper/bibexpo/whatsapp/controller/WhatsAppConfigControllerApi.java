package com.timekeeper.bibexpo.whatsapp.controller;

import com.timekeeper.bibexpo.exception.ErrorResponse;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.whatsapp.model.dto.request.SaveWhatsAppConfigRequest;
import com.timekeeper.bibexpo.whatsapp.model.dto.request.UpdateWhatsAppSenderModeRequest;
import com.timekeeper.bibexpo.whatsapp.model.dto.request.WhatsAppTestSendRequest;
import com.timekeeper.bibexpo.whatsapp.model.dto.response.WhatsAppConfigResponse;
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

@Tag(name = "Organization WhatsApp Configuration",
        description = "Per-organization Twilio WhatsApp sender: register credentials, switch between the application default and the organization's own account, verify with a test send")
@SecurityRequirement(name = "bearerAuth")
public interface WhatsAppConfigControllerApi {

    /**
     * Current WhatsApp sender configuration (mode, masked credentials, verification status).
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN')")
    @Operation(
            summary = "Get the organization's WhatsApp configuration",
            description = """
                    Returns the current sender mode and masked credentials. When no credentials \
                    are saved, `configured` is `false` and `mode` is `DEFAULT`. The auth token is \
                    never returned — only a masked tail. ROOT and ADMIN can view any organization; \
                    ORGANIZER_ADMIN only their own."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Configuration retrieved",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = WhatsAppConfigResponse.class))),
            @ApiResponse(responseCode = "403", description = "Not the caller's organization",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Organization not found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<WhatsAppConfigResponse> getConfig(
            @PathVariable Long organizationId,
            @AuthenticationPrincipal User currentUser);

    /**
     * Create or replace the organization's own Twilio credentials; switches mode to CUSTOM.
     */
    @PutMapping
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN')")
    @Operation(
            summary = "Save the organization's own WhatsApp credentials",
            description = """
                    Creates or replaces the organization's Twilio account SID, auth token and \
                    WhatsApp sender number, and switches the organization to `CUSTOM` mode. \
                    The auth token is encrypted at rest. Replacing credentials clears the \
                    previous verification status (`verified` becomes `false`)."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Credentials saved",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = WhatsAppConfigResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation failure",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Not the caller's organization",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Organization not found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<WhatsAppConfigResponse> saveConfig(
            @PathVariable Long organizationId,
            @Valid @RequestBody SaveWhatsAppConfigRequest request,
            @AuthenticationPrincipal User currentUser);

    /**
     * Switch between the application default sender and the organization's own account.
     */
    @PatchMapping("/mode")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN')")
    @Operation(
            summary = "Switch the WhatsApp sender mode",
            description = """
                    Flips the organization between `DEFAULT` (application sender) and `CUSTOM` \
                    (its own account). Saved credentials are retained when switching to `DEFAULT`, \
                    so switching back requires no re-entry. Switching to `CUSTOM` is rejected if \
                    no credentials are saved."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Mode updated",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = WhatsAppConfigResponse.class))),
            @ApiResponse(responseCode = "403", description = "Not the caller's organization",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Organization or saved credentials not found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<WhatsAppConfigResponse> updateMode(
            @PathVariable Long organizationId,
            @Valid @RequestBody UpdateWhatsAppSenderModeRequest request,
            @AuthenticationPrincipal User currentUser);

    /**
     * Verify the saved credentials by sending a template message to a given number.
     */
    @PostMapping("/test")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN')")
    @Operation(
            summary = "Test the organization's WhatsApp credentials",
            description = """
                    Sends the given approved Content Template to the given number using the \
                    organization's **saved** credentials (regardless of the current mode). \
                    On success the configuration's `verified` flag is set to `true`; a gateway \
                    failure sets it to `false` and returns `400`, so the stored verification \
                    always reflects the most recent test rather than a past success."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Test message accepted by Twilio",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = WhatsAppConfigResponse.class))),
            @ApiResponse(responseCode = "400", description = "Credentials could not be verified — verification status cleared",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Not the caller's organization",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Organization or saved credentials not found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<WhatsAppConfigResponse> testSend(
            @PathVariable Long organizationId,
            @Valid @RequestBody WhatsAppTestSendRequest request,
            @AuthenticationPrincipal User currentUser);

    /**
     * Remove the saved credentials entirely; the organization falls back to the default sender.
     */
    @DeleteMapping
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN')")
    @Operation(
            summary = "Delete the organization's WhatsApp credentials",
            description = """
                    Removes the saved Twilio credentials entirely. The organization falls back \
                    to the application default sender. Unlike switching to `DEFAULT` mode, this \
                    does not retain the credentials."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Credentials removed"),
            @ApiResponse(responseCode = "403", description = "Not the caller's organization",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Organization or saved credentials not found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<Void> deleteConfig(
            @PathVariable Long organizationId,
            @AuthenticationPrincipal User currentUser);
}
