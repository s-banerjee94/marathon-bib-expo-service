package com.timekeeper.bibexpo.messaging.provider.controller;

import com.timekeeper.bibexpo.messaging.provider.model.dto.request.SaveMessagingProviderRequest;
import com.timekeeper.bibexpo.messaging.provider.model.dto.response.MessagingProviderResponse;
import com.timekeeper.bibexpo.messaging.shared.enums.MessageChannel;
import com.timekeeper.bibexpo.shared.error.ErrorResponse;
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
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Root-only management of the per-channel system-message provider connections (how a message is
 * sent: endpoint, auth, request-parameter mapping). Secrets are masked on read.
 */
@Tag(name = "System Messaging Providers",
        description = "Root configuration of the SMS/WhatsApp provider connections used for system messages")
@RequestMapping("/api/system/messaging-providers")
@SecurityRequirement(name = "bearerAuth")
public interface SystemMessagingProviderControllerApi {

    @Operation(summary = "List all provider configurations",
            description = "**Roles:** `ROOT`\n\nEvery configured provider connection, secrets masked.")
    @ApiResponse(responseCode = "200", description = "Providers retrieved")
    @GetMapping
    @PreAuthorize("hasAnyRole('ROLE_ROOT')")
    ResponseEntity<List<MessagingProviderResponse>> list();

    @Operation(summary = "Get one channel's provider configuration",
            description = "**Roles:** `ROOT`\n\nThe provider connection for the channel, secrets masked.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Provider retrieved",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = MessagingProviderResponse.class))),
            @ApiResponse(responseCode = "404", description = "No provider configured for the channel",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{channel}")
    @PreAuthorize("hasAnyRole('ROLE_ROOT')")
    ResponseEntity<MessagingProviderResponse> get(
            @Parameter(description = "Channel to read", example = "SMS") @PathVariable MessageChannel channel);

    @Operation(summary = "Create or replace a channel's provider configuration",
            description = """
                    **Roles:** `ROOT`

                    Upserts the provider connection for the channel. Secret fields (authToken, \
                    password) are write-only — leave them blank to keep the stored value. An enabled provider \
                    must have an endpoint and a request mapping that matches its templateMode.""")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Provider saved",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = MessagingProviderResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation failed, or the request mapping does not match the provider's mode",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/{channel}")
    @PreAuthorize("hasAnyRole('ROLE_ROOT')")
    ResponseEntity<MessagingProviderResponse> save(
            @Parameter(description = "Channel to configure", example = "SMS") @PathVariable MessageChannel channel,
            @Valid @RequestBody SaveMessagingProviderRequest request);
}
