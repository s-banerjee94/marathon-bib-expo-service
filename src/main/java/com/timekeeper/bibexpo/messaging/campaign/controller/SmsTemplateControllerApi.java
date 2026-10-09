package com.timekeeper.bibexpo.messaging.campaign.controller;

import com.timekeeper.bibexpo.messaging.campaign.model.dto.request.CreateSmsTemplateRequest;
import com.timekeeper.bibexpo.messaging.campaign.model.dto.request.UpdateSmsTemplateRequest;
import com.timekeeper.bibexpo.messaging.campaign.model.dto.response.SmsTemplateResponse;
import com.timekeeper.bibexpo.shared.error.ErrorResponse;
import com.timekeeper.bibexpo.user.model.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "SMS Template Management", description = "APIs for managing SMS templates for marathon events")
@SecurityRequirement(name = "bearerAuth")
public interface SmsTemplateControllerApi {

    /**
     * Create a new SMS template for an event
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @Operation(
            summary = "Create a new SMS template",
            description = """
                    **Roles:** `ROOT`, `ADMIN`, `ORGANIZER_ADMIN`, `ORGANIZER_USER`

                    Create a new SMS template for an event. An event can have as many SMS templates as its \
                    limits allow (20 by default). \
                    Organizer roles can only create templates for their organization's events."""
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "SMS template created successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = SmsTemplateResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request data, invalid placeholders, or message text and variables that do not suit the current sender",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Access forbidden - user does not have permission",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Event not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "The DLT template ID is already used in this event, or the event's SMS template limit is reached",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<SmsTemplateResponse> createSmsTemplate(
            @PathVariable Long eventId,
            @Valid @RequestBody CreateSmsTemplateRequest request,
            @AuthenticationPrincipal User currentUser);

    /**
     * Update an existing SMS template
     */
    @PatchMapping("/{templateId}")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @Operation(
            summary = "Update an SMS template",
            description = """
                    **Roles:** `ROOT`, `ADMIN`, `ORGANIZER_ADMIN`, `ORGANIZER_USER`

                    Update an existing SMS template. A template used by a campaign that is sending or has \
                    been sent cannot be changed. \
                    Organizer roles can only update templates in their organization's events."""
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "SMS template updated successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = SmsTemplateResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request data, invalid placeholders, a field the template's mode does not use, or the template is used by a sending or sent campaign",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Access forbidden - user does not have permission",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "SMS template or event not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "The DLT template ID is already used in this event",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<SmsTemplateResponse> updateSmsTemplate(
            @PathVariable Long eventId,
            @PathVariable Long templateId,
            @Valid @RequestBody UpdateSmsTemplateRequest request,
            @AuthenticationPrincipal User currentUser);

    /**
     * Get all SMS templates for an event
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @Operation(
            summary = "Get all SMS templates for an event",
            description = """
                    **Roles:** `ROOT`, `ADMIN`, `ORGANIZER_ADMIN`, `ORGANIZER_USER`

                    Retrieve all SMS templates for an event with optional search, unpaged. \
                    Organizer roles can only view templates in their organization's events."""
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "SMS templates retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = SmsTemplateResponse.class))
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Access forbidden - user does not have permission",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Event not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<List<SmsTemplateResponse>> getSmsTemplatesByEvent(
            @PathVariable Long eventId,
            @Parameter(description = "Partial match on template name or DLT template ID")
            @RequestParam(required = false) String search,
            @AuthenticationPrincipal User currentUser);

    /**
     * Get an SMS template by ID
     */
    @GetMapping("/{templateId}")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @Operation(
            summary = "Get an SMS template by ID",
            description = """
                    **Roles:** `ROOT`, `ADMIN`, `ORGANIZER_ADMIN`, `ORGANIZER_USER`

                    Retrieve a single SMS template by its ID. \
                    Organizer roles can only view templates in their organization's events."""
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "SMS template retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = SmsTemplateResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Access forbidden - user does not have permission",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "SMS template or event not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<SmsTemplateResponse> getSmsTemplateById(
            @PathVariable Long eventId,
            @PathVariable Long templateId,
            @AuthenticationPrincipal User currentUser);

    /**
     * Get an SMS template by DLT Template ID
     */
    @GetMapping("/by-template-id/{smsTemplateId}")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @Operation(
            summary = "Get an SMS template by DLT Template ID",
            description = """
                    **Roles:** `ROOT`, `ADMIN`, `ORGANIZER_ADMIN`, `ORGANIZER_USER`

                    Retrieve a single SMS template by its DLT Template ID. \
                    Organizer roles can only view templates in their organization's events."""
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "SMS template retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = SmsTemplateResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Access forbidden - user does not have permission",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "SMS template or event not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<SmsTemplateResponse> getSmsTemplateBySmsTemplateId(
            @PathVariable Long eventId,
            @PathVariable String smsTemplateId,
            @AuthenticationPrincipal User currentUser);

    /**
     * Delete an SMS template
     */
    @DeleteMapping("/{templateId}")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @Operation(
            summary = "Delete an SMS template",
            description = """
                    **Roles:** `ROOT`, `ADMIN`, `ORGANIZER_ADMIN`, `ORGANIZER_USER`

                    Delete an SMS template. Cannot delete a template that is referenced by one or more campaigns. \
                    Organizer roles can only delete templates in their organization's events."""
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "204",
                    description = "SMS template deleted successfully"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Template is in use by one or more campaigns",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Access forbidden - user does not have permission",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "SMS template or event not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<Void> deleteSmsTemplate(
            @PathVariable Long eventId,
            @PathVariable Long templateId,
            @AuthenticationPrincipal User currentUser);
}
