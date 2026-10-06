package com.timekeeper.bibexpo.messaging.provider.controller;

import com.timekeeper.bibexpo.messaging.provider.model.dto.request.ProviderTestSendRequest;
import com.timekeeper.bibexpo.messaging.provider.model.dto.request.SaveMessagingProviderRequest;
import com.timekeeper.bibexpo.messaging.provider.model.dto.response.MessagingProviderResponse;
import com.timekeeper.bibexpo.messaging.shared.enums.MessageChannel;
import com.timekeeper.bibexpo.shared.error.ErrorResponse;
import com.timekeeper.bibexpo.user.model.entity.User;
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
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * An organization's own campaign sender configuration per channel, overriding the platform default.
 * Secrets are masked on read. ROOT and ADMIN may manage any organization; ORGANIZER_ADMIN only their own.
 */
@Tag(name = "Organization Campaign Providers",
        description = "An organization's own SMS/WhatsApp campaign sender configuration, overriding the platform default")
@RequestMapping("/api/organizations/{organizationId}/campaign-providers")
@SecurityRequirement(name = "bearerAuth")
public interface OrganizationCampaignProviderControllerApi {

    @Operation(summary = "List the organization's campaign provider overrides",
            description = "**Roles:** `ROOT`, `ADMIN`, `ORGANIZER_ADMIN`\n\nThe organization's own campaign senders, secrets masked. `ORGANIZER_ADMIN` reaches only their own organization.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Providers retrieved"),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Organization not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN')")
    ResponseEntity<List<MessagingProviderResponse>> list(
            @PathVariable Long organizationId,
            @AuthenticationPrincipal User currentUser);

    @Operation(summary = "Get one channel's campaign provider override",
            description = "**Roles:** `ROOT`, `ADMIN`, `ORGANIZER_ADMIN`\n\nThe organization's own sender for the channel, secrets masked.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Provider retrieved",
                    content = @Content(schema = @Schema(implementation = MessagingProviderResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Organization not found, or no override configured",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{channel}")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN')")
    ResponseEntity<MessagingProviderResponse> get(
            @PathVariable Long organizationId,
            @Parameter(description = "Channel", example = "WHATSAPP") @PathVariable MessageChannel channel,
            @AuthenticationPrincipal User currentUser);

    @Operation(summary = "Create or replace the organization's provider for a channel",
            description = """
                    **Roles:** `ROOT`, `ADMIN`, `ORGANIZER_ADMIN`

                    Secret fields (authToken, password) are write-only — leave them blank to keep the stored value. \
                    An enabled provider must have an endpoint and a request mapping that matches its templateMode. \
                    Switching an enabled sender off is refused while the organization has armed campaigns.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Provider saved",
                    content = @Content(schema = @Schema(implementation = MessagingProviderResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation failed, or the request mapping does not match the provider's mode",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Organization not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "The organization still has armed campaigns on the sender being switched off",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/{channel}")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN')")
    ResponseEntity<MessagingProviderResponse> save(
            @PathVariable Long organizationId,
            @Parameter(description = "Channel", example = "WHATSAPP") @PathVariable MessageChannel channel,
            @Valid @RequestBody SaveMessagingProviderRequest request,
            @AuthenticationPrincipal User currentUser);

    @Operation(summary = "Delete the organization's provider for a channel",
            description = "**Roles:** `ROOT`, `ADMIN`, `ORGANIZER_ADMIN`\n\nCampaigns for the organization fall back to the platform-default provider. "
                    + "Removing an enabled sender is refused while the organization has armed campaigns.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Provider removed"),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Organization not found, or no override configured",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "The organization still has armed campaigns on the sender",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/{channel}")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN')")
    ResponseEntity<Void> delete(
            @PathVariable Long organizationId,
            @Parameter(description = "Channel", example = "WHATSAPP") @PathVariable MessageChannel channel,
            @AuthenticationPrincipal User currentUser);

    @Operation(summary = "Send a test message to verify the organization's provider",
            description = "**Roles:** `ROOT`, `ADMIN`, `ORGANIZER_ADMIN`\n\nSends one real message through the stored provider, even while it is switched off.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Test message accepted by the provider",
                    content = @Content(schema = @Schema(implementation = MessagingProviderResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation failed",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Organization not found, or no override configured",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "502", description = "Provider call failed",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{channel}/test")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN')")
    ResponseEntity<MessagingProviderResponse> testSend(
            @PathVariable Long organizationId,
            @Parameter(description = "Channel", example = "WHATSAPP") @PathVariable MessageChannel channel,
            @Valid @RequestBody ProviderTestSendRequest request,
            @AuthenticationPrincipal User currentUser);
}
