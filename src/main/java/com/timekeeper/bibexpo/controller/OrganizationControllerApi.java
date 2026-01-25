package com.timekeeper.bibexpo.controller;

import com.timekeeper.bibexpo.exception.ErrorResponse;
import com.timekeeper.bibexpo.model.dto.request.CreateOrganizationRequest;
import com.timekeeper.bibexpo.model.dto.request.UpdateOrganizationRequest;
import com.timekeeper.bibexpo.model.dto.response.OrganizationResponse;
import com.timekeeper.bibexpo.model.dto.response.PageableResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.timekeeper.bibexpo.model.entity.User;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "Organization Management", description = "APIs for managing organizations")
@SecurityRequirement(name = "bearerAuth")
public interface OrganizationControllerApi {

    @GetMapping
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN')")
    @Operation(
            summary = "Get all organizations with pagination",
            description = "Retrieve all organizations with optional filtering and pagination. Only accessible by ROOT or ADMIN users."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Organizations retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PageableResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Access forbidden - requires ROOT or ADMIN",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<PageableResponse<OrganizationResponse>> getAllOrganizations(
            @Parameter(description = "Filter by enabled status (true/false)")
            @RequestParam(required = false) Boolean enabled,
            @Parameter(description = "Filter by deleted status (true/false)")
            @RequestParam(required = false) Boolean deleted,
            @Parameter(description = "Search across organizer name, email, and phone number (partial match, case-insensitive)")
            @RequestParam(required = false) String search,
            @Parameter(description = "Pagination parameters")
            Pageable pageable,
            @AuthenticationPrincipal User currentUser);

    @PostMapping
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN')")
    @Operation(
            summary = "Create a new organization",
            description = "Create a new organization. Only accessible by ROOT or ADMIN users."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Organization created successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = OrganizationResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request data",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Access forbidden - requires ROOT or ADMIN",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Organization already exists",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<OrganizationResponse> createOrganization(
            @Valid @RequestBody CreateOrganizationRequest request);

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN')")
    @Operation(
            summary = "Update an organization",
            description = "Partially update an organization. ROOT and ADMIN can update any organization. ORGANIZER_ADMIN can only update their own organization."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Organization updated successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = OrganizationResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request data",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Access forbidden - insufficient permissions or trying to update another organization",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Organization not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Organization with the same email or tax ID already exists",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<OrganizationResponse> updateOrganization(
            @PathVariable Long id,
            @Valid @RequestBody UpdateOrganizationRequest request,
            @AuthenticationPrincipal User currentUser);

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN')")
    @Operation(
            summary = "Enable or disable an organization",
            description = """
                    Enable or disable an organization. Only accessible by ROOT or ADMIN users. \
                    When an organization is disabled, all its users will be automatically disabled. \
                    When an organization is enabled, its users will NOT be automatically enabled."""
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Organization status updated successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = OrganizationResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request data",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Access forbidden - requires ROOT or ADMIN",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Organization not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<OrganizationResponse> toggleOrganizationStatus(
            @PathVariable Long id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Enable/disable flag",
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Boolean.class, example = "false")
                    )
            )
            @RequestBody Boolean enabled);

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @Operation(
            summary = "Get organization by ID",
            description = "Retrieve organization details. ROOT/ADMIN can view any organization. ORGANIZER users can only view their own organization."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Organization retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = OrganizationResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Access forbidden - insufficient permissions or trying to view another organization",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Organization not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<OrganizationResponse> getOrganizationById(
            @PathVariable Long id,
            @AuthenticationPrincipal User currentUser);

    @GetMapping("/organization")
    @PreAuthorize("hasAnyRole('ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER', 'ROLE_DISTRIBUTOR')")
    @Operation(
            summary = "Get current user's organization",
            description = "Retrieve the organization details for the currently authenticated user."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Organization retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = OrganizationResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Access forbidden - user has no organization",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<OrganizationResponse> getCurrentUserOrganization(
            @AuthenticationPrincipal User currentUser);
}
