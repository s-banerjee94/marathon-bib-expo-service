package com.timekeeper.bibexpo.inventory.controller;

import com.timekeeper.bibexpo.inventory.model.dto.request.CreateInventoryTermRequest;
import com.timekeeper.bibexpo.inventory.model.dto.request.UpdateInventoryTermRequest;
import com.timekeeper.bibexpo.inventory.model.dto.response.InventoryTermListResponse;
import com.timekeeper.bibexpo.inventory.model.dto.response.InventoryTermResponse;
import com.timekeeper.bibexpo.inventory.model.enums.TermKind;
import com.timekeeper.bibexpo.shared.error.ErrorResponse;
import com.timekeeper.bibexpo.user.model.entity.User;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "Inventory Terms",
        description = "An organization's own vocabulary for item categories, location types and units")
@SecurityRequirement(name = "bearerAuth")
public interface InventoryTermControllerApi {

    @Operation(
            summary = "List terms of one kind",
            description = """
                    Returns two lists of the given kind: the platform defaults every organization shares, \
                    and this organization's own terms. Each list is sorted by name."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Terms retrieved successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = InventoryTermListResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Organization not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    ResponseEntity<InventoryTermListResponse> listTerms(
            @PathVariable Long organizationId,
            @Parameter(description = "Which vocabulary to list", required = true)
            @RequestParam TermKind kind,
            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser);

    @Operation(
            summary = "Get one term",
            description = "@throws InventoryTermNotFoundException if no such term exists for this organization"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Term retrieved successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = InventoryTermResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Organization or term not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{termId}")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    ResponseEntity<InventoryTermResponse> getTerm(
            @PathVariable Long organizationId,
            @PathVariable Long termId,
            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser);

    @Operation(
            summary = "Add a term",
            description = """
                    @throws InventoryTermAlreadyExistsException if a term of this kind with this name already exists
                    @throws InventoryTermLimitReachedException if the organization has no term slots left"""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Term created successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = InventoryTermResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request body",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "A term with this name already exists, or the term limit is reached",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    ResponseEntity<InventoryTermResponse> createTerm(
            @PathVariable Long organizationId,
            @Parameter(description = "Which vocabulary this term belongs to", required = true)
            @RequestParam TermKind kind,
            @Valid @RequestBody CreateInventoryTermRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser);

    @Operation(
            summary = "Rename a term",
            description = """
                    @throws InventoryTermNotFoundException if no such term exists for this organization
                    @throws InventoryTermAlreadyExistsException if the new name collides with another term of the same kind"""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Term renamed successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = InventoryTermResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request body",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Organization or term not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "A term with this name already exists",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/{termId}")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    ResponseEntity<InventoryTermResponse> updateTerm(
            @PathVariable Long organizationId,
            @PathVariable Long termId,
            @Valid @RequestBody UpdateInventoryTermRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser);

    @Operation(
            summary = "Delete a term",
            description = """
                    @throws InventoryTermNotFoundException if no such term exists for this organization
                    @throws InventoryTermInUseException if an item or location still references this term"""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Term deleted successfully"),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Organization or term not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "The term is still in use",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/{termId}")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    ResponseEntity<Void> deleteTerm(
            @PathVariable Long organizationId,
            @PathVariable Long termId,
            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser);
}
