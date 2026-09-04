package com.timekeeper.bibexpo.inventory.controller;

import com.timekeeper.bibexpo.inventory.model.dto.request.CreateInventoryTermRequest;
import com.timekeeper.bibexpo.inventory.model.dto.request.UpdateInventoryTermRequest;
import com.timekeeper.bibexpo.inventory.model.dto.response.InventoryTermResponse;
import com.timekeeper.bibexpo.inventory.model.enums.TermKind;
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

import java.util.List;

@Tag(name = "System Inventory Terms",
        description = "Root-only management of the platform default terms every organization sees")
@RequestMapping("/api/system/inventory/terms")
@SecurityRequirement(name = "bearerAuth")
public interface SystemInventoryTermControllerApi {

    @Operation(summary = "List platform default terms of one kind", description = "Root only.")
    @ApiResponse(responseCode = "200", description = "Terms retrieved successfully",
            content = @Content(mediaType = "application/json",
                    array = @ArraySchema(schema = @Schema(implementation = InventoryTermResponse.class))))
    @GetMapping
    @PreAuthorize("hasAnyRole('ROLE_ROOT')")
    ResponseEntity<List<InventoryTermResponse>> listTerms(
            @Parameter(description = "Which vocabulary to list", required = true)
            @RequestParam TermKind kind,
            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser);

    @Operation(summary = "Get one platform default term",
            description = "Root only. Returns one platform default term.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Term retrieved successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = InventoryTermResponse.class))),
            @ApiResponse(responseCode = "404", description = "Term not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{termId}")
    @PreAuthorize("hasAnyRole('ROLE_ROOT')")
    ResponseEntity<InventoryTermResponse> getTerm(
            @PathVariable Long termId,
            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser);

    @Operation(summary = "Add a platform default term",
            description = """
                    Root only. Adds a term every organization can use. Names must be unique within \
                    the kind.""")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Term created successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = InventoryTermResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request body",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "A term with this name already exists",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    @PreAuthorize("hasAnyRole('ROLE_ROOT')")
    ResponseEntity<InventoryTermResponse> createTerm(
            @Parameter(description = "Which vocabulary this term belongs to", required = true)
            @RequestParam TermKind kind,
            @Valid @RequestBody CreateInventoryTermRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser);

    @Operation(summary = "Rename a platform default term",
            description = """
                    Root only. Renames a term every organization can use.""")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Term renamed successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = InventoryTermResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request body",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Term not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "A term with this name already exists",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/{termId}")
    @PreAuthorize("hasAnyRole('ROLE_ROOT')")
    ResponseEntity<InventoryTermResponse> updateTerm(
            @PathVariable Long termId,
            @Valid @RequestBody UpdateInventoryTermRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser);

    @Operation(summary = "Delete a platform default term",
            description = """
                    Root only. Removes a platform default term. One an item or a location still points \
                    at cannot be removed.""")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Term deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Term not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "The term is still in use",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/{termId}")
    @PreAuthorize("hasAnyRole('ROLE_ROOT')")
    ResponseEntity<Void> deleteTerm(
            @PathVariable Long termId,
            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser);
}
