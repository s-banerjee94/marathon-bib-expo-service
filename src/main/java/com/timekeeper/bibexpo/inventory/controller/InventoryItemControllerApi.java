package com.timekeeper.bibexpo.inventory.controller;

import com.timekeeper.bibexpo.inventory.model.dto.request.CreateInventoryItemRequest;
import com.timekeeper.bibexpo.inventory.model.dto.request.CreateInventoryVariantRequest;
import com.timekeeper.bibexpo.inventory.model.dto.request.UpdateInventoryItemRequest;
import com.timekeeper.bibexpo.inventory.model.dto.response.InventoryItemResponse;
import com.timekeeper.bibexpo.inventory.model.dto.response.InventoryItemSummaryResponse;
import com.timekeeper.bibexpo.shared.error.ErrorResponse;
import com.timekeeper.bibexpo.shared.web.PageableResponse;
import com.timekeeper.bibexpo.storage.model.dto.request.AttachUploadRequest;
import com.timekeeper.bibexpo.storage.model.dto.request.PresignUploadRequest;
import com.timekeeper.bibexpo.storage.model.dto.response.PresignUploadResponse;
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
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Instant;

@Tag(name = "Inventory Items", description = "The things an organization stocks, and their variants")
@SecurityRequirement(name = "bearerAuth")
public interface InventoryItemControllerApi {

    @Operation(
            summary = "List items",
            description = """
                    One page of the organization's items. Each row carries the item alone — its name, \
                    category, unit, note and how many variants it has — but not the attributes or the
                    variants themselves. Fetch one item to see those.

                    Every filter is optional and they combine. Filtering happens in the database, so \
                    the page totals describe the filtered set, not the whole catalogue.

                    Newest first — by <code>createdAt</code> descending — unless a <code>sort</code> \
                    is given, which replaces it."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Items retrieved successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = PageableResponse.class))),
            @ApiResponse(responseCode = "400", description = "The date range starts after it ends",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Organization not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    ResponseEntity<PageableResponse<InventoryItemSummaryResponse>> listItems(
            @PathVariable Long organizationId,

            @Parameter(description = "Name fragment, matched anywhere in the name and ignoring case.",
                    example = "shirt")
            @RequestParam(required = false) String name,

            @Parameter(description = "Keep only items in this category term.", example = "12")
            @RequestParam(required = false) Long categoryId,

            @Parameter(description = "Keep only items added on or after this instant, ISO-8601.",
                    example = "2026-09-01T00:00:00Z")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdFrom,

            @Parameter(description = "Keep only items added on or before this instant, ISO-8601. Must not be before <code>createdFrom</code>.",
                    example = "2026-09-30T23:59:59Z")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdTo,

            @Parameter(description = "Pagination parameters") Pageable pageable,
            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser);

    @Operation(
            summary = "Get one item",
            description = "Returns one item with its own attribute values, its variants and theirs."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Item retrieved successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = InventoryItemResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Organization or item not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{itemId}")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    ResponseEntity<InventoryItemResponse> getItem(
            @PathVariable Long organizationId,
            @PathVariable Long itemId,
            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser);

    @Operation(
            summary = "Add an item",
            description = """
                    Adds a new item. Its category and unit must be terms this organization can see, \
                    and names must be unique within the organization. An item with no variants given \
                    gets one plain variant, so there is always something to count stock against."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Item created successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = InventoryItemResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request body",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Organization or term not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "An item with this name already exists",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    ResponseEntity<InventoryItemResponse> createItem(
            @PathVariable Long organizationId,
            @Valid @RequestBody CreateInventoryItemRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser);

    @Operation(
            summary = "Update an item",
            description = """
                    Updates an item's name, category, unit, low-stock threshold, or note. Fields left out \
                    of the request are unchanged, and an empty note clears the one already there.
                    Variants are changed through their own endpoints."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Item updated successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = InventoryItemResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request body",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Organization, item, or term not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "An item with this name already exists",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/{itemId}")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    ResponseEntity<InventoryItemResponse> updateItem(
            @PathVariable Long organizationId,
            @PathVariable Long itemId,
            @Valid @RequestBody UpdateInventoryItemRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser);

    @Operation(
            summary = "Delete an item",
            description = """
                    Deletes an item together with all of its variants. An item any of whose variants \
                    still has stock on hand cannot be deleted, and neither can one an event goody \
                    is handed out from — unlink the goody first."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Item deleted successfully"),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Organization or item not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "The item still has stock on hand, or an event goody is handed out from it",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/{itemId}")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    ResponseEntity<Void> deleteItem(
            @PathVariable Long organizationId,
            @PathVariable Long itemId,
            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser);

    @Operation(
            summary = "Add a variant",
            description = """
                    Adds one more variant to an existing item. Every variant of an item must vary by \
                    the same attributes, no two may carry the same combination of values, and both the \
                    number of variants and the number of attributes they vary by are capped by the \
                    organization's plan."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Variant added successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = InventoryItemResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request body",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Organization or item not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Duplicate attribute values, or a plan limit reached",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{itemId}/variants")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    ResponseEntity<InventoryItemResponse> addVariant(
            @PathVariable Long organizationId,
            @PathVariable Long itemId,
            @Valid @RequestBody CreateInventoryVariantRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser);

    @Operation(
            summary = "Remove a variant",
            description = """
                    Removes one variant from an item. An item must always keep at least one, and a \
                    variant that still has stock on hand cannot be removed."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Variant removed successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = InventoryItemResponse.class))),
            @ApiResponse(responseCode = "400", description = "The item's last remaining variant cannot be removed",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Organization, item, or variant not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "The variant still has stock on hand",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/{itemId}/variants/{variantId}")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    ResponseEntity<InventoryItemResponse> removeVariant(
            @PathVariable Long organizationId,
            @PathVariable Long itemId,
            @PathVariable Long variantId,
            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser);

    @Operation(
            summary = "Get a presigned URL to upload a variant image",
            description = """
                    Returns a short-lived presigned S3 PUT URL. The client uploads the file bytes \
                    directly to that URL with the given Content-Type, then calls the attach endpoint \
                    with the returned objectKey."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Presigned upload URL created",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = PresignUploadResponse.class))),
            @ApiResponse(responseCode = "400", description = "Unsupported file type",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Organization, item, or variant not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{itemId}/variants/{variantId}/image/upload-url")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    ResponseEntity<PresignUploadResponse> createVariantImageUploadUrl(
            @PathVariable Long organizationId,
            @PathVariable Long itemId,
            @PathVariable Long variantId,
            @Valid @RequestBody PresignUploadRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser);

    @Operation(
            summary = "Attach an uploaded variant image",
            description = """
                    Attaches a previously uploaded object as the variant's image, replacing and \
                    deleting any existing one."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Image attached successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = InventoryItemResponse.class))),
            @ApiResponse(responseCode = "400", description = "Object key invalid or file missing",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Organization, item, or variant not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/{itemId}/variants/{variantId}/image")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    ResponseEntity<InventoryItemResponse> attachVariantImage(
            @PathVariable Long organizationId,
            @PathVariable Long itemId,
            @PathVariable Long variantId,
            @Valid @RequestBody AttachUploadRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser);

    @Operation(
            summary = "Remove a variant image",
            description = "Deletes the variant's image from storage, if any."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Image removed successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = InventoryItemResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Organization, item, or variant not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/{itemId}/variants/{variantId}/image")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    ResponseEntity<InventoryItemResponse> removeVariantImage(
            @PathVariable Long organizationId,
            @PathVariable Long itemId,
            @PathVariable Long variantId,
            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser);
}
