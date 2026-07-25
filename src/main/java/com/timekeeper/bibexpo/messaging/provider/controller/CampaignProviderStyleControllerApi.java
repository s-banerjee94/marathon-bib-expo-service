package com.timekeeper.bibexpo.messaging.provider.controller;

import com.timekeeper.bibexpo.exception.ErrorResponse;
import com.timekeeper.bibexpo.messaging.provider.model.dto.response.CampaignProviderStyleResponse;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Read-only lookup that tells the campaign template editor how the effective campaign provider for an
 * event's organization wants a message rendered on a channel, so the UI can offer the matching author
 * experience (message text vs positional variables). Resolves the override-else-default row and exposes
 * only rendering hints — never the provider connection or secrets.
 */
@Tag(name = "Campaign Provider Style",
        description = "Effective campaign provider rendering hints for authoring campaign templates")
@RequestMapping("/api/events/{eventId}/campaign-provider-style")
@SecurityRequirement(name = "bearerAuth")
public interface CampaignProviderStyleControllerApi {

    /**
     * Resolve the effective campaign provider's rendering style for a channel.
     */
    @Operation(summary = "Get the effective campaign provider's rendering style for a channel",
            description = """
                    Returns the rendering hints of the provider that will send campaigns for this event's \
                    organization on the given channel: the organization's own override if enabled, otherwise \
                    the platform default. `hasProvider` is false when neither exists — the UI should prompt to \
                    configure one before authoring. No connection details or secrets are returned.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Rendering style resolved",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = CampaignProviderStyleResponse.class))),
            @ApiResponse(responseCode = "404", description = "Event not found or not visible to the caller",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    ResponseEntity<CampaignProviderStyleResponse> getStyle(
            @Parameter(description = "Event whose organization's provider to resolve", example = "1")
            @PathVariable Long eventId,
            @Parameter(description = "Channel to resolve", example = "SMS")
            @RequestParam MessageChannel channel,
            @AuthenticationPrincipal User currentUser);
}
