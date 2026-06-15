package com.timekeeper.bibexpo.messaging.campaign.controller;

import com.timekeeper.bibexpo.exception.ErrorResponse;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.messaging.campaign.model.dto.request.CreateWhatsAppTemplateRequest;
import com.timekeeper.bibexpo.messaging.campaign.model.dto.request.UpdateWhatsAppTemplateRequest;
import com.timekeeper.bibexpo.messaging.campaign.model.dto.response.WhatsAppTemplateResponse;
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

import java.util.List;

@Tag(name = "WhatsApp Template Management",
        description = "Registry of approved Twilio Content Templates per event; the Content SID plays the role the DLT template ID plays for SMS")
@SecurityRequirement(name = "bearerAuth")
public interface WhatsAppTemplateControllerApi {

    /**
     * Register an approved Content Template for an event.
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @Operation(
            summary = "Create a WhatsApp template",
            description = """
                    Registers an approved Twilio Content Template (by its `HX…` Content SID) for an \
                    event. `body` is the approved message text with positional `{{n}}` markers, stored \
                    so the message is readable in the app. `bodyVariables` is an ordered list — entry \
                    *n* fills the Twilio template variable `{{n}}` — using the same `#{fieldName}` \
                    placeholders as SMS templates. The Content SID must exist in whichever WhatsApp \
                    campaign provider sends it (the organization's own provider, or the platform default). \
                    An event can have a maximum of 20 WhatsApp templates. Template creation and \
                    Meta approval happen in the Twilio Console beforehand."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Template created",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = WhatsAppTemplateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error, invalid placeholders, or template limit reached",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Event not found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Content SID already registered for this event",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<WhatsAppTemplateResponse> createTemplate(
            @PathVariable Long eventId,
            @Valid @RequestBody CreateWhatsAppTemplateRequest request,
            @AuthenticationPrincipal User currentUser);

    /**
     * List an event's WhatsApp templates.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @Operation(
            summary = "List WhatsApp templates",
            description = "Lists an event's WhatsApp templates, optionally filtered by a search term over name and Content SID."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Templates retrieved"),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Event not found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<List<WhatsAppTemplateResponse>> getTemplates(
            @PathVariable Long eventId,
            @RequestParam(required = false) String search,
            @AuthenticationPrincipal User currentUser);

    /**
     * Fetch a single WhatsApp template.
     */
    @GetMapping("/{templateId}")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @Operation(summary = "Get a WhatsApp template by ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Template retrieved",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = WhatsAppTemplateResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Event or template not found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<WhatsAppTemplateResponse> getTemplateById(
            @PathVariable Long eventId,
            @PathVariable Long templateId,
            @AuthenticationPrincipal User currentUser);

    /**
     * Partially update a WhatsApp template.
     */
    @PatchMapping("/{templateId}")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @Operation(
            summary = "Update a WhatsApp template",
            description = "Partially updates a template; absent fields are left unchanged. Sending an empty `bodyVariables` list clears the variables."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Template updated",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = WhatsAppTemplateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error or invalid placeholders",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Event or template not found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Content SID already registered for this event",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<WhatsAppTemplateResponse> updateTemplate(
            @PathVariable Long eventId,
            @PathVariable Long templateId,
            @Valid @RequestBody UpdateWhatsAppTemplateRequest request,
            @AuthenticationPrincipal User currentUser);

    /**
     * Delete a WhatsApp template.
     */
    @DeleteMapping("/{templateId}")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @Operation(summary = "Delete a WhatsApp template")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Template deleted"),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Event or template not found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<Void> deleteTemplate(
            @PathVariable Long eventId,
            @PathVariable Long templateId,
            @AuthenticationPrincipal User currentUser);
}
