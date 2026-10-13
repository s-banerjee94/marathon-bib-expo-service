package com.timekeeper.bibexpo.inventory.controller;

import com.timekeeper.bibexpo.inventory.model.dto.request.ApplyVariantImageRequest;
import com.timekeeper.bibexpo.inventory.model.dto.request.CreateInventoryItemRequest;
import com.timekeeper.bibexpo.inventory.model.dto.request.CreateInventoryVariantRequest;
import com.timekeeper.bibexpo.inventory.model.dto.request.UpdateInventoryItemRequest;
import com.timekeeper.bibexpo.inventory.model.dto.response.InventoryItemResponse;
import com.timekeeper.bibexpo.inventory.model.dto.response.InventoryItemSummaryResponse;
import com.timekeeper.bibexpo.shared.error.ErrorResponse;
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

import java.util.List;

@Tag(name = "Inventory Items", description = "The things an organization stocks, and their variants")
@SecurityRequirement(name = "bearerAuth")
public interface InventoryItemControllerApi {

    @Operation(
            summary = "List items",
            description = """
                    **Roles:** `ROOT`, `ADMIN`, `ORGANIZER_ADMIN`, `ORGANIZER_USER`

                    Every item of the organization as <code>{id, name}</code>, in name order, for the \
                    item pickers. Not paged: the organization's item quota bounds the list. Fetch one \
                    item for its attributes and variants, or <code>GET /stock</code> for stock."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Items retrieved successfully"),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Organization not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    ResponseEntity<List<InventoryItemSummaryResponse>> listItems(
            @PathVariable Long organizationId,
            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser);

    @Operation(
            summary = "Get one item",
            description = "**Roles:** `ROOT`, `ADMIN`, `ORGANIZER_ADMIN`, `ORGANIZER_USER`\n\nReturns one item with its own attribute values, its variants and theirs. Every item write answers the same shape. Besides the short-lived <code>imageUrl</code>s, it carries the stored keys: <code>primaryImageKey</code> on the item and <code>imageKey</code> on each variant, so a photo already there can be made primary or shown on other variants without a second upload."
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
                    **Roles:** `ROOT`, `ADMIN`, `ORGANIZER_ADMIN`, `ORGANIZER_USER`

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
            @ApiResponse(responseCode = "409", description = "An item with this name already exists, two variants carry the same values, or a plan limit is reached",
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
                    **Roles:** `ROOT`, `ADMIN`, `ORGANIZER_ADMIN`, `ORGANIZER_USER`

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
                    **Roles:** `ROOT`, `ADMIN`, `ORGANIZER_ADMIN`, `ORGANIZER_USER`

                    Deletes an item together with all of its variants. It can be deleted only once \
                    every variant's stock is back to exactly zero at every location: a balance above \
                    zero would vanish, and one below zero means hand-outs took more than the shelf \
                    held, which an adjustment or receipt must settle first. Neither can an item an \
                    event goody is handed out from be deleted — unlink the goody first."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Item deleted successfully"),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Organization or item not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "A variant's stock is not zero somewhere (above or below), or an event goody is handed out from it",
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
                    **Roles:** `ROOT`, `ADMIN`, `ORGANIZER_ADMIN`, `ORGANIZER_USER`

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
                    **Roles:** `ROOT`, `ADMIN`, `ORGANIZER_ADMIN`, `ORGANIZER_USER`

                    Removes one variant from an item. An item must always keep at least one. A \
                    variant can be removed only once its stock is back to exactly zero at every \
                    location — a shelf below zero, left by hand-outs beyond what it held, blocks it \
                    too, until an adjustment or receipt settles it — and not while the item still \
                    reads a roster spelling as it."""
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
            @ApiResponse(responseCode = "409", description = "The variant's stock is not zero somewhere (above or below), or the item still reads a spelling as it",
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
            summary = "Show one photo on several variants",
            description = """
                    **Roles:** `ROOT`, `ADMIN`, `ORGANIZER_ADMIN`, `ORGANIZER_USER`

                    Shows one uploaded photo on every listed variant, for example all four sizes of the \
                    Red T-Shirt, so they share one stored file. Upload it first through the item's \
                    <code>image/upload-url</code>. A key this item or one of its variants already uses \
                    is accepted too, so a photo already on one variant can be spread without a second \
                    upload; any other key is refused.

                    A variant's previous photo is only unlinked. Its file is deleted once no variant and \
                    not the item itself use it any more."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Photo applied successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = InventoryItemResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request body, an object key that does not belong to this item, or a file that was not uploaded",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Organization or item not found, or a variant that is not this item's",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/{itemId}/variants/image")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    ResponseEntity<InventoryItemResponse> applyVariantImage(
            @PathVariable Long organizationId,
            @PathVariable Long itemId,
            @Valid @RequestBody ApplyVariantImageRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser);

    @Operation(
            summary = "Remove a variant image",
            description = "**Roles:** `ROOT`, `ADMIN`, `ORGANIZER_ADMIN`, `ORGANIZER_USER`\n\nUnlinks the variant's photo, if any. Its file is deleted once no variant and not the item itself use it any more."
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

    @Operation(
            summary = "Get a presigned URL to upload the item's photo",
            description = """
                    **Roles:** `ROOT`, `ADMIN`, `ORGANIZER_ADMIN`, `ORGANIZER_USER`

                    Returns a short-lived presigned S3 PUT URL for a photo of this item. The client \
                    uploads the file bytes directly to that URL with the given Content-Type, then uses the \
                    returned objectKey to make it the item's primary photo, to show it on variants, or \
                    both, all without a second upload."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Presigned upload URL created",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = PresignUploadResponse.class))),
            @ApiResponse(responseCode = "400", description = "Unsupported file type",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Organization or item not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{itemId}/image/upload-url")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    ResponseEntity<PresignUploadResponse> createItemImageUploadUrl(
            @PathVariable Long organizationId,
            @PathVariable Long itemId,
            @Valid @RequestBody PresignUploadRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser);

    @Operation(
            summary = "Attach an uploaded item photo",
            description = """
                    **Roles:** `ROOT`, `ADMIN`, `ORGANIZER_ADMIN`, `ORGANIZER_USER`

                    Makes a photo the item's primary photo, which every list's <code>imageUrl</code> then \
                    shows. The key comes from the item's upload URL, or is one a variant of this item \
                    already uses, so a variant photo can be made primary without a second upload. The \
                    previous primary photo is only unlinked; its file is deleted once no variant uses it."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Photo attached successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = InventoryItemResponse.class))),
            @ApiResponse(responseCode = "400", description = "An object key that does not belong to this item, or a file that was not uploaded",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Organization or item not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/{itemId}/image")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    ResponseEntity<InventoryItemResponse> attachItemImage(
            @PathVariable Long organizationId,
            @PathVariable Long itemId,
            @Valid @RequestBody AttachUploadRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser);

    @Operation(
            summary = "Remove the item photo",
            description = """
                    **Roles:** `ROOT`, `ADMIN`, `ORGANIZER_ADMIN`, `ORGANIZER_USER`

                    Unlinks the item's primary photo, if any; its file is deleted once no variant uses it. \
                    The item's <code>imageUrl</code> then falls back to the first variant photo."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Photo removed successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = InventoryItemResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Organization or item not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/{itemId}/image")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    ResponseEntity<InventoryItemResponse> removeItemImage(
            @PathVariable Long organizationId,
            @PathVariable Long itemId,
            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser);
}
