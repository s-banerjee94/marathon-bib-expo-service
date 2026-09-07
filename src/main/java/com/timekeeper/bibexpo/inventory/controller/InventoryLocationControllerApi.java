package com.timekeeper.bibexpo.inventory.controller;

import com.timekeeper.bibexpo.inventory.model.dto.request.CreateInventoryLocationRequest;
import com.timekeeper.bibexpo.inventory.model.dto.request.UpdateInventoryLocationRequest;
import com.timekeeper.bibexpo.inventory.model.dto.response.InventoryLocationResponse;
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
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Tag(name = "Inventory Locations", description = "Where an organization's stock physically sits")
@SecurityRequirement(name = "bearerAuth")
public interface InventoryLocationControllerApi {

    @Operation(
            summary = "List locations",
            description = """
                    The organization's locations, in name order. Both filters are optional and they \
                    combine; each one left out narrows nothing.

                    The whole list comes back at once — an organization is capped at a number of \
                    locations a single response holds comfortably, so there is nothing to page."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Locations retrieved successfully",
                    content = @Content(mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = InventoryLocationResponse.class)))),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Organization not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    ResponseEntity<List<InventoryLocationResponse>> listLocations(
            @PathVariable Long organizationId,

            @Parameter(description = "Keep only locations whose name contains this text, ignoring case.",
                    example = "expo")
            @RequestParam(required = false) String name,

            @Parameter(description = "Keep only locations of this type.", example = "7")
            @RequestParam(required = false) Long typeId,

            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser);

    @Operation(
            summary = "Add a location",
            description = """
                    Adds a place where stock can sit. The type must be a location-type term this \
                    organization can see, and names must be unique within the organization. The \
                    number of locations one organization may keep is capped by its plan."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Location created successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = InventoryLocationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request body",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Organization or term not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "A location with this name already exists, or the organization has reached its location limit",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    ResponseEntity<InventoryLocationResponse> createLocation(
            @PathVariable Long organizationId,
            @Valid @RequestBody CreateInventoryLocationRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser);

    @Operation(
            summary = "Rename or retype a location",
            description = """
                    Renames a location, changes its type, or both; fields left out are unchanged."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Location updated successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = InventoryLocationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request body",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Organization, location, or term not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "A location with this name already exists",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/{locationId}")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    ResponseEntity<InventoryLocationResponse> updateLocation(
            @PathVariable Long organizationId,
            @PathVariable Long locationId,
            @Valid @RequestBody UpdateInventoryLocationRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser);

    @Operation(
            summary = "Delete a location",
            description = """
                    Removes a location no stock has ever moved through — a typo, or a place that \
                    turned out not to be needed. Once a location appears on the ledger it is kept \
                    for good, since the history names it; rename it instead."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Location deleted successfully"),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Organization or location not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Stock has already moved through this location",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/{locationId}")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    ResponseEntity<Void> deleteLocation(
            @PathVariable Long organizationId,
            @PathVariable Long locationId,
            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser);
}
