package com.timekeeper.bibexpo.messaging.provider.controller;

import com.timekeeper.bibexpo.exception.ErrorResponse;
import com.timekeeper.bibexpo.messaging.provider.model.dto.request.ProviderTestSendRequest;
import com.timekeeper.bibexpo.messaging.provider.model.dto.request.SaveMessagingProviderRequest;
import com.timekeeper.bibexpo.messaging.provider.model.dto.response.MessagingProviderResponse;
import com.timekeeper.bibexpo.messaging.shared.enums.MessageChannel;
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
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Root-only configuration of the platform-default campaign senders per channel — the providers used
 * for any organization that has not set its own override. Secrets are masked on read.
 */
@Tag(name = "Default Campaign Providers",
        description = "Root configuration of the platform-default SMS/WhatsApp campaign senders")
@RequestMapping("/api/system/campaign-providers")
@SecurityRequirement(name = "bearerAuth")
public interface DefaultCampaignProviderControllerApi {

    @Operation(summary = "List the platform-default campaign providers")
    @ApiResponse(responseCode = "200", description = "Providers retrieved")
    @GetMapping
    @PreAuthorize("hasAnyRole('ROLE_ROOT')")
    ResponseEntity<List<MessagingProviderResponse>> list(@AuthenticationPrincipal User currentUser);

    @Operation(summary = "Get one channel's platform-default campaign provider")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Provider retrieved",
                    content = @Content(schema = @Schema(implementation = MessagingProviderResponse.class))),
            @ApiResponse(responseCode = "404", description = "No default configured",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{channel}")
    @PreAuthorize("hasAnyRole('ROLE_ROOT')")
    ResponseEntity<MessagingProviderResponse> get(
            @Parameter(description = "Channel", example = "SMS") @PathVariable MessageChannel channel,
            @AuthenticationPrincipal User currentUser);

    @Operation(summary = "Create or replace a channel's platform-default campaign provider",
            description = "Secret fields (authToken, password) are write-only — leave blank to keep the stored value.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Provider saved",
                    content = @Content(schema = @Schema(implementation = MessagingProviderResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation failure",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/{channel}")
    @PreAuthorize("hasAnyRole('ROLE_ROOT')")
    ResponseEntity<MessagingProviderResponse> save(
            @Parameter(description = "Channel", example = "SMS") @PathVariable MessageChannel channel,
            @Valid @RequestBody SaveMessagingProviderRequest request,
            @AuthenticationPrincipal User currentUser);

    @Operation(summary = "Delete a channel's platform-default campaign provider")
    @ApiResponse(responseCode = "204", description = "Provider removed")
    @DeleteMapping("/{channel}")
    @PreAuthorize("hasAnyRole('ROLE_ROOT')")
    ResponseEntity<Void> delete(
            @Parameter(description = "Channel", example = "SMS") @PathVariable MessageChannel channel,
            @AuthenticationPrincipal User currentUser);

    @Operation(summary = "Send a test message to verify the platform-default provider")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Test message accepted by the provider",
                    content = @Content(schema = @Schema(implementation = MessagingProviderResponse.class))),
            @ApiResponse(responseCode = "502", description = "Provider call failed",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{channel}/test")
    @PreAuthorize("hasAnyRole('ROLE_ROOT')")
    ResponseEntity<MessagingProviderResponse> testSend(
            @Parameter(description = "Channel", example = "SMS") @PathVariable MessageChannel channel,
            @Valid @RequestBody ProviderTestSendRequest request,
            @AuthenticationPrincipal User currentUser);
}
