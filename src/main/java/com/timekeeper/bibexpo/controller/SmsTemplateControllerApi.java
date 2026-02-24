package com.timekeeper.bibexpo.controller;

import com.timekeeper.bibexpo.exception.ErrorResponse;
import com.timekeeper.bibexpo.model.dto.request.CreateSmsTemplateRequest;
import com.timekeeper.bibexpo.model.dto.request.UpdateSmsTemplateRequest;
import com.timekeeper.bibexpo.model.dto.response.PageableResponse;
import com.timekeeper.bibexpo.model.dto.response.SmsTemplateResponse;
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
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
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
                    Create a new SMS template for an event. ROOT and ADMIN can create templates for any event. \
                    ORGANIZER_ADMIN and ORGANIZER_USER can only create templates for their organization's events."""
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
                    description = "Invalid request data or validation error",
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
                    description = "SMS template ID already exists for this event",
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
                    Update an existing SMS template. ROOT and ADMIN can update any template. \
                    ORGANIZER_ADMIN and ORGANIZER_USER can only update templates in their organization's events."""
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
                    description = "Invalid request data or validation error",
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
                    description = "SMS template ID already exists for this event",
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
     * Get all SMS templates for an event (paginated)
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @Operation(
            summary = "Get all SMS templates for an event",
            description = """
                    Retrieve all SMS templates for an event with optional enabledOnly filter and pagination. \
                    ROOT and ADMIN can view templates for any event. \
                    ORGANIZER_ADMIN and ORGANIZER_USER can only view templates in their organization's events."""
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "SMS templates retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PageableResponse.class)
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
    ResponseEntity<PageableResponse<SmsTemplateResponse>> getSmsTemplatesByEvent(
            @PathVariable Long eventId,
            @Parameter(description = "Partial match on template name or DLT template ID")
            @RequestParam(required = false) String search,
            @Parameter(description = "Filter by enabled status (true/false, omit for all)")
            @RequestParam(required = false) Boolean enabled,
            @Parameter(description = "Filter by scheduled date from (inclusive, format: yyyy-MM-dd)")
            @RequestParam(required = false) LocalDate fromDate,
            @Parameter(description = "Filter by scheduled date to (inclusive, format: yyyy-MM-dd)")
            @RequestParam(required = false) LocalDate toDate,
            @Parameter(description = "Pagination and sorting parameters")
            Pageable pageable,
            @AuthenticationPrincipal User currentUser);

    /**
     * Get an SMS template by ID
     */
    @GetMapping("/{templateId}")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @Operation(
            summary = "Get an SMS template by ID",
            description = """
                    Retrieve a single SMS template by its ID. \
                    ROOT and ADMIN can view any template. \
                    ORGANIZER_ADMIN and ORGANIZER_USER can only view templates in their organization's events."""
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
                    Retrieve a single SMS template by its DLT Template ID. \
                    ROOT and ADMIN can view any template. \
                    ORGANIZER_ADMIN and ORGANIZER_USER can only view templates in their organization's events."""
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
     * Toggle SMS template enabled status
     */
    @PatchMapping("/{templateId}/toggle-enabled")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @Operation(
            summary = "Toggle SMS template enabled status",
            description = """
                    Toggle the enabled status of an SMS template. \
                    ROOT and ADMIN can toggle any template. \
                    ORGANIZER_ADMIN and ORGANIZER_USER can only toggle templates in their organization's events."""
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "SMS template enabled status toggled successfully",
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
    ResponseEntity<SmsTemplateResponse> toggleSmsTemplateEnabled(
            @PathVariable Long eventId,
            @PathVariable Long templateId,
            @AuthenticationPrincipal User currentUser);

    /**
     * Delete an SMS template
     */
    @DeleteMapping("/{templateId}")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @Operation(
            summary = "Delete an SMS template",
            description = """
                    Delete an SMS template. \
                    ROOT and ADMIN can delete any template. \
                    ORGANIZER_ADMIN and ORGANIZER_USER can only delete templates in their organization's events."""
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "204",
                    description = "SMS template deleted successfully"
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
