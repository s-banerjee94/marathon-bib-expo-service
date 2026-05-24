package com.timekeeper.bibexpo.controller;

import com.timekeeper.bibexpo.exception.ErrorResponse;
import com.timekeeper.bibexpo.model.dto.request.CreateCategoryRequest;
import com.timekeeper.bibexpo.model.dto.request.UpdateCategoryRequest;
import com.timekeeper.bibexpo.model.dto.response.CategoryResponse;
import com.timekeeper.bibexpo.model.entity.Gender;
import com.timekeeper.bibexpo.model.entity.User;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Category Management", description = "APIs for managing categories within race events")
@SecurityRequirement(name = "bearerAuth")
public interface CategoryControllerApi {

    @GetMapping
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER', 'ROLE_DISTRIBUTOR')")
    @Operation(
            summary = "Get all categories for a race",
            description = """
                    Retrieve all categories for a specific race with optional gender filtering. \
                    ROOT and ADMIN can access categories for any race. \
                    ORGANIZER_ADMIN, ORGANIZER_USER and DISTRIBUTOR can only access categories for races in their organization's events."""
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Categories retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = CategoryResponse.class))
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Access forbidden - trying to access race from another organization",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Event or Race not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<List<CategoryResponse>> getCategoriesByRaceId(
            @Parameter(description = "Event ID", required = true)
            @PathVariable Long eventId,
            @Parameter(description = "Race ID", required = true)
            @PathVariable Long raceId,
            @Parameter(description = "Filter by gender")
            @RequestParam(required = false) Gender gender,
            @AuthenticationPrincipal User currentUser);

    @GetMapping("/{categoryId}")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER', 'ROLE_DISTRIBUTOR')")
    @Operation(
            summary = "Get category by ID",
            description = """
                    Retrieve a specific category by its ID. \
                    ROOT and ADMIN can view any category. \
                    ORGANIZER_ADMIN and ORGANIZER_USER can only view categories from their organization's events. \
                    DISTRIBUTOR can view categories from their organization's events."""
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Category retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CategoryResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Access forbidden - trying to view category from another organization",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Category, Race, or Event not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<CategoryResponse> getCategoryById(
            @Parameter(description = "Event ID", required = true)
            @PathVariable Long eventId,
            @Parameter(description = "Race ID", required = true)
            @PathVariable Long raceId,
            @Parameter(description = "Category ID", required = true)
            @PathVariable Long categoryId,
            @AuthenticationPrincipal User currentUser);

    @PostMapping
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @Operation(
            summary = "Create a new category for a race",
            description = """
                    Create a new category within a race. ROOT and ADMIN can create categories for any race. \
                    ORGANIZER_ADMIN and ORGANIZER_USER can only create categories for races in their organization's events."""
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Category created successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CategoryResponse.class)
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
                    description = "Access forbidden - insufficient permissions or trying to create category for another organization",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Event or Race not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Category with the same name already exists for this race",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<CategoryResponse> createCategory(
            @Parameter(description = "Event ID", required = true)
            @PathVariable Long eventId,
            @Parameter(description = "Race ID", required = true)
            @PathVariable Long raceId,
            @Valid @RequestBody CreateCategoryRequest request,
            @AuthenticationPrincipal User currentUser);

    @PatchMapping("/{categoryId}")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @Operation(
            summary = "Update an existing category",
            description = """
                    Update an existing category. Only provided fields will be updated (partial update). \
                    ROOT and ADMIN can update categories for any race. \
                    ORGANIZER_ADMIN and ORGANIZER_USER can only update categories for races in their organization's events."""
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Category updated successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CategoryResponse.class)
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
                    description = "Access forbidden - insufficient permissions or trying to update category from another organization",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Category, Race, or Event not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Category with the same name already exists for this race",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<CategoryResponse> updateCategory(
            @Parameter(description = "Event ID", required = true)
            @PathVariable Long eventId,
            @Parameter(description = "Race ID", required = true)
            @PathVariable Long raceId,
            @Parameter(description = "Category ID", required = true)
            @PathVariable Long categoryId,
            @Valid @RequestBody UpdateCategoryRequest request,
            @AuthenticationPrincipal User currentUser);

    @DeleteMapping("/{categoryId}")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @Operation(
            summary = "Permanently delete a category",
            description = """
                    Permanently delete a category from the system. \
                    Deletion is blocked if participants are assigned to this category. \
                    Please reassign or delete participants before deleting the category. \
                    ROOT and ADMIN can delete any category. \
                    ORGANIZER_ADMIN and ORGANIZER_USER can only delete categories from their organization's events."""
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "204",
                    description = "Category deleted successfully"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Access forbidden - trying to delete category from another organization",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Category, Race, or Event not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Category cannot be deleted - has participants assigned",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<Void> deleteCategory(
            @Parameter(description = "Event ID", required = true)
            @PathVariable Long eventId,
            @Parameter(description = "Race ID", required = true)
            @PathVariable Long raceId,
            @Parameter(description = "Category ID", required = true)
            @PathVariable Long categoryId,
            @AuthenticationPrincipal User currentUser);
}
