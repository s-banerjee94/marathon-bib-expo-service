package com.timekeeper.bibexpo.messaging.direct.controller;

import com.timekeeper.bibexpo.exception.ErrorResponse;
import com.timekeeper.bibexpo.messaging.direct.model.dto.request.SendParticipantMessagesRequest;
import com.timekeeper.bibexpo.messaging.direct.model.dto.response.ParticipantMessagesResponse;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "Targeted Participant Messaging", description = "APIs for sending a message to named participants of an event")
@SecurityRequirement(name = "bearerAuth")
public interface ParticipantMessageControllerApi {

    /**
     * Send one template to up to 25 named participants of the event
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @Operation(
            summary = "Send a message to named participants",
            description = """
                    Sends one SMS or WhatsApp template to the bib numbers you list, instead of to the whole event. \
                    Use it when a participant reports never receiving an automatic message and asks for it again. \
                    Omit templateId to reuse the template of the event's active bib collection campaign for the channel. \
                    A maximum of 25 bib numbers per request; duplicates are ignored. \
                    Messages go out through the same provider a campaign on this channel would use, and are sent while the request is open. \
                    Campaign send history is untouched: a participant already covered by a campaign is not skipped, and this send never marks anyone as covered. \
                    Each bib number is attempted on its own — check the per-participant results, since some can fail while others succeed."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Send attempted for every listed bib number; inspect results for per-participant outcomes",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ParticipantMessagesResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation failed, no template could be resolved, or the event state does not allow sending",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Event or template not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "502", description = "No messaging provider is configured for the channel",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<ParticipantMessagesResponse> sendToParticipants(
            @Parameter(description = "Event ID", example = "1") @PathVariable Long eventId,
            @Valid @RequestBody SendParticipantMessagesRequest request,
            @AuthenticationPrincipal User currentUser);
}
