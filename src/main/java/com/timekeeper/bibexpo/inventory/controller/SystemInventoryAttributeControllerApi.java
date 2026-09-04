package com.timekeeper.bibexpo.inventory.controller;

import com.timekeeper.bibexpo.inventory.model.dto.request.CreateInventoryAttributeRequest;
import com.timekeeper.bibexpo.inventory.model.dto.request.CreateInventoryAttributeOptionRequest;
import com.timekeeper.bibexpo.inventory.model.dto.request.UpdateInventoryAttributeRequest;
import com.timekeeper.bibexpo.inventory.model.dto.request.UpdateInventoryAttributeOptionRequest;
import com.timekeeper.bibexpo.inventory.model.dto.response.InventoryAttributeResponse;
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

import java.util.List;

@Tag(name = "System Inventory Attributes",
        description = "Root-only management of the platform default attributes every organization sees")
@RequestMapping("/api/system/inventory/attributes")
@SecurityRequirement(name = "bearerAuth")
public interface SystemInventoryAttributeControllerApi {

    @Operation(summary = "List platform default attributes", description = "Root only.")
    @ApiResponse(responseCode = "200", description = "Attributes retrieved successfully",
            content = @Content(mediaType = "application/json",
                    array = @ArraySchema(schema = @Schema(implementation = InventoryAttributeResponse.class))))
    @GetMapping
    @PreAuthorize("hasAnyRole('ROLE_ROOT')")
    ResponseEntity<List<InventoryAttributeResponse>> listAttributes(
            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser);

    @Operation(summary = "Get one platform default attribute",
            description = "@throws InventoryAttributeNotFoundException if no such platform default exists")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Attribute retrieved successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = InventoryAttributeResponse.class))),
            @ApiResponse(responseCode = "404", description = "Attribute not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{attributeId}")
    @PreAuthorize("hasAnyRole('ROLE_ROOT')")
    ResponseEntity<InventoryAttributeResponse> getAttribute(
            @PathVariable Long attributeId,
            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser);

    @Operation(summary = "Add a platform default attribute",
            description = """
                    Root only. \

                    @throws InventoryAttributeAlreadyExistsException if a platform default with this name already exists""")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Attribute created successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = InventoryAttributeResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request body, or a non-SELECT attribute was asked to define variants",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "An attribute with this name already exists",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    @PreAuthorize("hasAnyRole('ROLE_ROOT')")
    ResponseEntity<InventoryAttributeResponse> createAttribute(
            @Valid @RequestBody CreateInventoryAttributeRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser);

    @Operation(summary = "Rename a platform default attribute or change whether it is required",
            description = """
                    Root only. type and variantAttribute cannot be changed after creation.

                    @throws InventoryAttributeNotFoundException if no such platform default exists
                    @throws InventoryAttributeAlreadyExistsException if the new name collides with another platform default""")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Attribute updated successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = InventoryAttributeResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request body",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Attribute not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "An attribute with this name already exists",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/{attributeId}")
    @PreAuthorize("hasAnyRole('ROLE_ROOT')")
    ResponseEntity<InventoryAttributeResponse> updateAttribute(
            @PathVariable Long attributeId,
            @Valid @RequestBody UpdateInventoryAttributeRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser);

    @Operation(summary = "Delete a platform default attribute",
            description = """
                    Root only. \

                    @throws InventoryAttributeNotFoundException if no such platform default exists
                    @throws InventoryAttributeInUseException if an item or variant still references it""")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Attribute deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Attribute not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "The attribute is still in use",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/{attributeId}")
    @PreAuthorize("hasAnyRole('ROLE_ROOT')")
    ResponseEntity<Void> deleteAttribute(
            @PathVariable Long attributeId,
            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser);

    @Operation(summary = "Add an allowed value to a platform default attribute",
            description = """
                    Root only. Only a SELECT attribute can have a list of allowed values.

                    @throws InventoryAttributeNotFoundException if no such platform default exists
                    @throws InventoryAttributeOptionAlreadyExistsException if this value already exists for the attribute""")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Value added successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = InventoryAttributeResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request body, or the attribute is not SELECT-typed",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Attribute not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "This value already exists for the attribute",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{attributeId}/options")
    @PreAuthorize("hasAnyRole('ROLE_ROOT')")
    ResponseEntity<InventoryAttributeResponse> addOption(
            @PathVariable Long attributeId,
            @Valid @RequestBody CreateInventoryAttributeOptionRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser);

    @Operation(summary = "Rename an allowed value on a platform default attribute",
            description = """
                    Root only. \

                    @throws InventoryAttributeOptionNotFoundException if the value does not belong to this attribute
                    @throws InventoryAttributeOptionAlreadyExistsException if the new value collides with another on the attribute""")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Value renamed successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = InventoryAttributeResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request body",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Attribute or value not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "This value already exists for the attribute",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/{attributeId}/options/{optionId}")
    @PreAuthorize("hasAnyRole('ROLE_ROOT')")
    ResponseEntity<InventoryAttributeResponse> updateOption(
            @PathVariable Long attributeId,
            @PathVariable Long optionId,
            @Valid @RequestBody UpdateInventoryAttributeOptionRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser);

    @Operation(summary = "Remove an allowed value from a platform default attribute",
            description = """
                    Root only. \

                    @throws InventoryAttributeOptionNotFoundException if the value does not belong to this attribute
                    @throws InventoryAttributeOptionInUseException if any item or variant still carries this value""")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Value removed successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = InventoryAttributeResponse.class))),
            @ApiResponse(responseCode = "404", description = "Attribute or value not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "The value is still in use",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/{attributeId}/options/{optionId}")
    @PreAuthorize("hasAnyRole('ROLE_ROOT')")
    ResponseEntity<InventoryAttributeResponse> removeOption(
            @PathVariable Long attributeId,
            @PathVariable Long optionId,
            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser);
}
