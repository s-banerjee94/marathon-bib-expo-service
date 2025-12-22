package com.timekeeper.bibexpo.controller;

import com.timekeeper.bibexpo.exception.ErrorResponse;
import com.timekeeper.bibexpo.model.dto.request.CreateUserRequest;
import com.timekeeper.bibexpo.model.dto.request.UpdateUserRequest;
import com.timekeeper.bibexpo.model.dto.response.UserResponse;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * API interface for user management operations
 */
@Tag(name = "User Management", description = "APIs for managing users")
@RequestMapping("/api/users")
@SecurityRequirement(name = "basicAuth")
public interface UserControllerApi {

    @Operation(
            summary = "Create a new user",
            description = "Creates a new user with specified role. " +
                         "ROOT can create: ADMIN, ORG_ADMIN, ORG_USER, DISTRIBUTOR (any organization). " +
                         "ADMIN can create: ORG_ADMIN, ORG_USER, DISTRIBUTOR (any organization, but NOT ADMIN). " +
                         "ORG_ADMIN can create: ORG_USER, DISTRIBUTOR (only within their own organization). " +
                         "Cannot create ROLE_ROOT (system-initialized only). " +
                         "Organization user limits (maxOrganizerUsers, maxDistributors) are enforced."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "User created successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UserResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request data, validation failed, or organization limit exceeded",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - Insufficient permissions, attempt to create unauthorized role, " +
                                 "organization limit exceeded, or ORG_ADMIN attempting to create user outside their organization",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Organization not found (when organizationId is provided)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "User already exists (username or email conflict)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    @PostMapping
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN')")
    ResponseEntity<UserResponse> createUser(
            @Parameter(description = "User creation request", required = true)
            @Valid @RequestBody CreateUserRequest request,

            @Parameter(hidden = true)
            @AuthenticationPrincipal User currentUser
    );

    @Operation(
            summary = "Update user profile",
            description = "Partially updates a user's profile (PATCH operation). " +
                         "Only basic profile fields can be updated: password, email, fullName, phoneNumber. " +
                         "Administrative fields (role, organization, account status) require separate admin operations. " +
                         "Permission hierarchy: " +
                         "ROOT can update any user. " +
                         "ADMIN can update itself and lower-level users (but not ROOT or other ADMINs). " +
                         "ORG_ADMIN can update itself and users in their organization (but not ROOT, ADMIN, or other ORG_ADMINs). " +
                         "ORG_USER and DISTRIBUTOR can only update their own profile. " +
                         "All fields in the request are optional - only provided fields will be updated."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "User updated successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UserResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request data or validation failed",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - Insufficient permissions to update this user",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "User or organization not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Email conflict (email already exists)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    @PatchMapping("/{userId}")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER', 'ROLE_DISTRIBUTOR')")
    ResponseEntity<UserResponse> updateUser(
            @Parameter(description = "User ID", required = true)
            @PathVariable Long userId,

            @Parameter(description = "User update request", required = true)
            @Valid @RequestBody UpdateUserRequest request,

            @Parameter(hidden = true)
            @AuthenticationPrincipal User currentUser
    );

    @Operation(
            summary = "Toggle user enabled status",
            description = "Toggles the enabled/disabled status of a user account. " +
                         "This is an administrative operation. " +
                         "Permission hierarchy: " +
                         "ROOT can disable any user. " +
                         "ADMIN can disable any user. " +
                         "ORG_ADMIN can disable ORG_USER and DISTRIBUTOR (own organization only). " +
                         "ORG_USER can disable DISTRIBUTOR (own organization only)."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "User enabled status toggled successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UserResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - Insufficient permissions to toggle this user's status",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "User not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    @PatchMapping("/{userId}/toggle-enabled")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    ResponseEntity<UserResponse> toggleUserEnabled(
            @Parameter(description = "User ID", required = true)
            @PathVariable Long userId,

            @Parameter(hidden = true)
            @AuthenticationPrincipal User currentUser
    );

    @Operation(
            summary = "Get user by ID",
            description = "Retrieves a single user by their ID. " +
                         "Permission hierarchy: " +
                         "ROOT and ADMIN can get any user. " +
                         "ORG_ADMIN, ORG_USER, and DISTRIBUTOR can only get users in their organization. " +
                         "Deleted users return 404 Not Found."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "User retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UserResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - Insufficient permissions to view this user",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "User not found or deleted",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    @GetMapping("/{userId}")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER', 'ROLE_DISTRIBUTOR')")
    ResponseEntity<UserResponse> getUserById(
            @Parameter(description = "User ID", required = true)
            @PathVariable Long userId,

            @Parameter(hidden = true)
            @AuthenticationPrincipal User currentUser
    );

    @Operation(
            summary = "Get all users in the system (ROOT/ADMIN only)",
            description = "Retrieves all users in the system with optional filtering, searching, and sorting. " +
                         "Only ROOT and ADMIN users can access this endpoint. " +
                         "Can query any organization and include deleted users. " +
                         "Returns a simple list (no pagination)."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Users retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UserResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - User is not ROOT or ADMIN",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    @GetMapping
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN')")
    ResponseEntity<List<UserResponse>> getAllUsers(
            @Parameter(description = "Filter by user role")
            @RequestParam(required = false) com.timekeeper.bibexpo.model.entity.UserRole role,

            @Parameter(description = "Filter by organization ID")
            @RequestParam(required = false) Long organizationId,

            @Parameter(description = "Filter by enabled status")
            @RequestParam(required = false) Boolean enabled,

            @Parameter(description = "Include deleted users (default: false)")
            @RequestParam(required = false, defaultValue = "false") Boolean includeDeleted,

            @Parameter(description = "Search by username, email, or full name (case-insensitive)")
            @RequestParam(required = false) String search,

            @Parameter(description = "Sort by field (username, email, fullName, role, createdAt)")
            @RequestParam(required = false) String sortBy,

            @Parameter(description = "Sort direction (ASC, DESC, default: ASC)")
            @RequestParam(required = false, defaultValue = "ASC") String sortDirection,

            @Parameter(hidden = true)
            @AuthenticationPrincipal User currentUser
    );

    @Operation(
            summary = "Get users in current user's organization",
            description = "Retrieves all users in the current user's organization with optional filtering, searching, and sorting. " +
                         "Only ORG_ADMIN, ORG_USER, and DISTRIBUTOR can access this endpoint. " +
                         "Automatically scoped to the user's organization. Cannot include deleted users. " +
                         "Returns a simple list (no pagination)."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Organization users retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UserResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - User is not an organization user or has no organization assigned",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    @GetMapping("/organization")
    @PreAuthorize("hasAnyRole('ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER', 'ROLE_DISTRIBUTOR')")
    ResponseEntity<List<UserResponse>> getOrganizationUsers(
            @Parameter(description = "Filter by user role")
            @RequestParam(required = false) com.timekeeper.bibexpo.model.entity.UserRole role,

            @Parameter(description = "Filter by enabled status")
            @RequestParam(required = false) Boolean enabled,

            @Parameter(description = "Search by username, email, or full name (case-insensitive)")
            @RequestParam(required = false) String search,

            @Parameter(description = "Sort by field (username, email, fullName, role, createdAt)")
            @RequestParam(required = false) String sortBy,

            @Parameter(description = "Sort direction (ASC, DESC, default: ASC)")
            @RequestParam(required = false, defaultValue = "ASC") String sortDirection,

            @Parameter(hidden = true)
            @AuthenticationPrincipal User currentUser
    );

    @Operation(
            summary = "Get current user profile",
            description = "Retrieves the profile of the currently authenticated user. " +
                         "All authenticated users can access their own profile regardless of role."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Current user profile retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UserResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - User is not authenticated",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER', 'ROLE_DISTRIBUTOR')")
    ResponseEntity<UserResponse> getCurrentUser(
            @Parameter(hidden = true)
            @AuthenticationPrincipal User currentUser
    );
}
