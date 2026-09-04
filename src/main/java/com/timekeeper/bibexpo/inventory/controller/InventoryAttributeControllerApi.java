package com.timekeeper.bibexpo.inventory.controller;

import com.timekeeper.bibexpo.inventory.model.dto.request.CreateInventoryAttributeRequest;
import com.timekeeper.bibexpo.inventory.model.dto.request.CreateInventoryAttributeOptionRequest;
import com.timekeeper.bibexpo.inventory.model.dto.request.UpdateInventoryAttributeRequest;
import com.timekeeper.bibexpo.inventory.model.dto.request.UpdateInventoryAttributeOptionRequest;
import com.timekeeper.bibexpo.inventory.model.dto.response.InventoryAttributeListResponse;
import com.timekeeper.bibexpo.inventory.model.dto.response.InventoryAttributeResponse;
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

@Tag(name = "Inventory Attributes",
        description = "An organization's reusable attributes and their allowed values")
@SecurityRequirement(name = "bearerAuth")
public interface InventoryAttributeControllerApi {

    @Operation(
            summary = "List attributes",
            description = """
                    Returns two lists: the platform defaults every organization shares, and this \
                    organization's own attributes. Each list is sorted by name."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Attributes retrieved successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = InventoryAttributeListResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Organization not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    ResponseEntity<InventoryAttributeListResponse> listAttributes(
            @PathVariable Long organizationId,
            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser);

    @Operation(
            summary = "Get one attribute",
            description = "@throws InventoryAttributeNotFoundException if no such attribute exists for this organization"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Attribute retrieved successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = InventoryAttributeResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Organization or attribute not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{attributeId}")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    ResponseEntity<InventoryAttributeResponse> getAttribute(
            @PathVariable Long organizationId,
            @PathVariable Long attributeId,
            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser);

    @Operation(
            summary = "Add an attribute",
            description = "@throws InventoryAttributeAlreadyExistsException if an attribute with this name already exists"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Attribute created successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = InventoryAttributeResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request body, or a non-SELECT attribute was asked to define variants",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "An attribute with this name already exists",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    ResponseEntity<InventoryAttributeResponse> createAttribute(
            @PathVariable Long organizationId,
            @Valid @RequestBody CreateInventoryAttributeRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser);

    @Operation(
            summary = "Rename an attribute or change whether it is required",
            description = """
                    type and variantAttribute cannot be changed after creation.

                    @throws InventoryAttributeNotFoundException if no such attribute exists for this organization
                    @throws InventoryAttributeAlreadyExistsException if the new name collides with another attribute"""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Attribute updated successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = InventoryAttributeResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request body",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Organization or attribute not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "An attribute with this name already exists",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/{attributeId}")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    ResponseEntity<InventoryAttributeResponse> updateAttribute(
            @PathVariable Long organizationId,
            @PathVariable Long attributeId,
            @Valid @RequestBody UpdateInventoryAttributeRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser);

    @Operation(
            summary = "Delete an attribute",
            description = """
                    @throws InventoryAttributeNotFoundException if no such attribute exists for this organization
                    @throws InventoryAttributeInUseException if an item or variant still references it"""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Attribute deleted successfully"),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Organization or attribute not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "The attribute is still in use",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/{attributeId}")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    ResponseEntity<Void> deleteAttribute(
            @PathVariable Long organizationId,
            @PathVariable Long attributeId,
            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser);

    @Operation(
            summary = "Add an allowed value",
            description = """
                    Only a SELECT attribute can have a list of allowed values.

                    @throws InventoryAttributeNotFoundException if no such attribute exists for this organization
                    @throws InventoryAttributeOptionAlreadyExistsException if this value already exists for the attribute
                    @throws InventoryAttributeOptionLimitReachedException if the attribute has no value slots left"""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Value added successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = InventoryAttributeResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request body, or the attribute is not SELECT-typed",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Organization or attribute not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "This value already exists for the attribute, or the attribute has no value slots left",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{attributeId}/options")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    ResponseEntity<InventoryAttributeResponse> addOption(
            @PathVariable Long organizationId,
            @PathVariable Long attributeId,
            @Valid @RequestBody CreateInventoryAttributeOptionRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser);

    @Operation(
            summary = "Rename an allowed value",
            description = """
                    @throws InventoryAttributeOptionNotFoundException if the value does not belong to this attribute
                    @throws InventoryAttributeOptionAlreadyExistsException if the new value collides with another on the attribute"""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Value renamed successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = InventoryAttributeResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request body",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Organization, attribute, or value not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "This value already exists for the attribute",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/{attributeId}/options/{optionId}")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    ResponseEntity<InventoryAttributeResponse> updateOption(
            @PathVariable Long organizationId,
            @PathVariable Long attributeId,
            @PathVariable Long optionId,
            @Valid @RequestBody UpdateInventoryAttributeOptionRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser);

    @Operation(
            summary = "Remove an allowed value",
            description = """
                    @throws InventoryAttributeOptionNotFoundException if the value does not belong to this attribute
                    @throws InventoryAttributeOptionInUseException if any item or variant still carries this value"""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Value removed successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = InventoryAttributeResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Organization, attribute, or value not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "The value is still in use",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/{attributeId}/options/{optionId}")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    ResponseEntity<InventoryAttributeResponse> removeOption(
            @PathVariable Long organizationId,
            @PathVariable Long attributeId,
            @PathVariable Long optionId,
            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser);
}
