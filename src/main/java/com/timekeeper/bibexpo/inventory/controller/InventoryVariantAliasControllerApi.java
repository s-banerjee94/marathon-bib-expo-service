package com.timekeeper.bibexpo.inventory.controller;

import com.timekeeper.bibexpo.inventory.model.dto.request.CreateInventoryVariantAliasRequest;
import com.timekeeper.bibexpo.inventory.model.dto.request.UpdateInventoryVariantAliasRequest;
import com.timekeeper.bibexpo.inventory.model.dto.response.InventoryVariantAliasResponse;
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

import java.util.List;

@Tag(name = "Inventory Variant Aliases",
        description = "The spellings imported rosters use for an item, and what each one means")
@SecurityRequirement(name = "bearerAuth")
public interface InventoryVariantAliasControllerApi {

    @Operation(
            summary = "Read an item's spellings",
            description = """
                    Every roster spelling this item understands beyond its own variant values, in \
                    spelling order.

                    Most items need none. A roster cell is first matched against the item's own \
                    variant values, ignoring case and surrounding spaces, so a file that already \
                    says S / M / L is understood as it stands. Aliases are for the rest: a file \
                    saying M where this item's sizes read 38, or Medium where they read M."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Spellings retrieved successfully",
                    content = @Content(mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = InventoryVariantAliasResponse.class)))),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Organization or item not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    ResponseEntity<List<InventoryVariantAliasResponse>> listAliases(
            @PathVariable Long organizationId,
            @PathVariable Long itemId,
            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser);

    @Operation(
            summary = "Teach the item a spelling",
            description = """
                    Records that one spelling found in rosters means one variant of this item.

                    Leave the variant out to record the opposite: that the spelling is owed nothing \
                    at all, such as "No" in a medal column. It then counts as no demand, and the \
                    counter hands the goody over without taking anything off the shelf. A blank cell, \
                    which imports store as "Not mentioned", usually still means the goody is owed: \
                    teach it the variant those participants get.

                    A spelling belongs to the item, not to an event, because it means what \
                    it means by virtue of which product this is. The next event using this item \
                    inherits them. Spellings that differ between rosters — M one year, Medium \
                    the next — simply accumulate as separate lines pointing at the same variant."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Spelling added successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = InventoryVariantAliasResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request body",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Organization, item, or variant not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "This item already reads that spelling",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    ResponseEntity<InventoryVariantAliasResponse> createAlias(
            @PathVariable Long organizationId,
            @PathVariable Long itemId,
            @Valid @RequestBody CreateInventoryVariantAliasRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser);

    @Operation(
            summary = "Point a spelling at a different variant",
            description = """
                    Changes what one spelling means, or sends a null variant to make it mean \
                    nothing at all. The spelling itself is the line's identity and is never changed \
                    here — remove the line and add it again to correct a typo."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Spelling updated successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = InventoryVariantAliasResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Organization, item, spelling, or variant not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/{aliasId}")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    ResponseEntity<InventoryVariantAliasResponse> updateAlias(
            @PathVariable Long organizationId,
            @PathVariable Long itemId,
            @PathVariable Long aliasId,
            @Valid @RequestBody UpdateInventoryVariantAliasRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser);

    @Operation(
            summary = "Forget a spelling",
            description = """
                    Removes one line. The spelling simply stops being understood and goes back to \
                    needing attention; the item, its variants and its stock are untouched."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Spelling removed successfully"),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Organization, item, or spelling not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/{aliasId}")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    ResponseEntity<Void> deleteAlias(
            @PathVariable Long organizationId,
            @PathVariable Long itemId,
            @PathVariable Long aliasId,
            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser);
}
