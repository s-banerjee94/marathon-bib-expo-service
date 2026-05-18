package com.timekeeper.bibexpo.controller;

import com.timekeeper.bibexpo.exception.ErrorResponse;
import com.timekeeper.bibexpo.model.dto.request.CreateUserRequest;
import com.timekeeper.bibexpo.model.dto.request.UpdateUserRequest;
import com.timekeeper.bibexpo.model.dto.response.PageableResponse;
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
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * API interface for user management operations
 */
@Tag(name = "User Management", description = "APIs for managing users")
@RequestMapping("/api/users")
@SecurityRequirement(name = "bearerAuth")
public interface UserControllerApi {

    @Operation(
            summary = "Create a new user",
            description = """
                    Creates a new user with specified role. \
                    ROOT can create: ADMIN, ORGANIZER_ADMIN, ORGANIZER_USER, DISTRIBUTOR (any organization). \
                    ADMIN can create: ORGANIZER_ADMIN, ORGANIZER_USER, DISTRIBUTOR (any organization). \
                    ORGANIZER_ADMIN can create: ORGANIZER_USER, DISTRIBUTOR (own organization only). \
                    ORGANIZER_USER can create: DISTRIBUTOR (own organization only). \
                    Cannot create ROOT (system-initialized only). \
                    Organization user limits (maxOrganizerUsers, maxDistributors) are enforced."""
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
                    description = """
                            Forbidden - Insufficient permissions to create the requested role, \
                            or ORG_ADMIN/ORG_USER attempting to create user outside their own organization""",
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
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    ResponseEntity<UserResponse> createUser(
            @Parameter(description = "User creation request", required = true)
            @Valid @RequestBody CreateUserRequest request,

            @Parameter(hidden = true)
            @AuthenticationPrincipal User currentUser
    );

    @Operation(
            summary = "Update user profile",
            description = """
                    Partially updates a user's profile (PATCH operation). \
                    Only basic profile fields can be updated: password, email, fullName, phoneNumber. \
                    Administrative fields (role, organization, account status) require separate admin operations. \
                    Permission hierarchy: \
                    ROOT can update any user. \
                    ADMIN can update itself and lower-level users (but not ROOT or other ADMINs). \
                    ORG_ADMIN can update itself and users in their organization (but not ROOT, ADMIN, or other ORG_ADMINs). \
                    ORG_USER can update their own profile and DISTRIBUTOR accounts in their organization. \
                    DISTRIBUTOR can only update their own profile. \
                    All fields in the request are optional - only provided fields will be updated."""
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
            description = """
                    Toggles the enabled/disabled status of a user account. \
                    This is an administrative operation. \
                    Permission hierarchy: \
                    ROOT can disable any user. \
                    ADMIN can disable any user. \
                    ORG_ADMIN can disable ORG_USER and DISTRIBUTOR (own organization only). \
                    ORG_USER can disable themselves and DISTRIBUTOR (own organization only)."""
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
            description = """
                    Retrieves a single user by their ID. \
                    Permission hierarchy: \
                    ROOT and ADMIN can get any user. \
                    ORG_ADMIN, ORG_USER, and DISTRIBUTOR can only get users in their organization. \
                    Archived users return 404 Not Found."""
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
                    description = "User not found or archived",
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
            summary = "Get users",
            description = """
                    Retrieves users with optional filtering, searching, and pagination. \
                    ROOT and ADMIN: full access across all organizations, organizationId param honored. \
                    ORGANIZER_ADMIN and ORGANIZER_USER: automatically scoped to their own organization, \
                    organizationId param is ignored."""
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Users retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PageableResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - Insufficient permissions or no organization assigned",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    @GetMapping
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    ResponseEntity<PageableResponse<UserResponse>> getUsers(
            @Parameter(description = "Filter by user role")
            @RequestParam(required = false) com.timekeeper.bibexpo.model.entity.UserRole role,

            @Parameter(description = "Filter by organization ID (ROOT/ADMIN only)")
            @RequestParam(required = false) Long organizationId,

            @Parameter(description = "Filter by enabled status")
            @RequestParam(required = false) Boolean enabled,

            @Parameter(description = "Search by username, email, or full name (case-insensitive)")
            @RequestParam(required = false) String search,

            @Parameter(description = "Pagination parameters")
            Pageable pageable,

            @Parameter(hidden = true)
            @AuthenticationPrincipal User currentUser
    );


    @Operation(
            summary = "Get current user profile",
            description = """
                    Retrieves the profile of the currently authenticated user. \
                    All authenticated users can access their own profile regardless of role."""
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

    @Operation(
            summary = "Archive (delete) a user",
            description = """
                    Archives a user by moving their record to the users_archive table and \
                    deleting their notifications. The user's username, email, and phone number \
                    become available for reuse. ROOT users cannot be archived. \
                    Permission hierarchy: \
                    ROOT can archive any non-ROOT user. \
                    ADMIN can archive ORG_ADMIN, ORG_USER, DISTRIBUTOR (not itself or other ADMINs). \
                    ORG_ADMIN can archive ORG_USER, DISTRIBUTOR in own organization. \
                    ORG_USER can archive DISTRIBUTOR in own organization. \
                    DISTRIBUTOR cannot archive users."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "User archived successfully"),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - Insufficient permissions or target is ROOT",
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
    @DeleteMapping("/{userId}")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    ResponseEntity<Void> deleteUser(
            @Parameter(description = "User ID", required = true)
            @PathVariable Long userId,

            @Parameter(hidden = true)
            @AuthenticationPrincipal User currentUser
    );
}
