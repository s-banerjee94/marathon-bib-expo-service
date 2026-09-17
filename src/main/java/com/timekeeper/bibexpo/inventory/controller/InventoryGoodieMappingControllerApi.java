package com.timekeeper.bibexpo.inventory.controller;

import com.timekeeper.bibexpo.inventory.model.dto.request.CreateInventoryGoodieMappingRequest;
import com.timekeeper.bibexpo.inventory.model.dto.request.UpdateInventoryGoodieMappingRequest;
import com.timekeeper.bibexpo.inventory.model.dto.response.InventoryGoodieMappingResponse;
import com.timekeeper.bibexpo.inventory.model.dto.response.InventoryGoodieResolutionResponse;
import com.timekeeper.bibexpo.inventory.model.dto.response.InventoryGoodieShortfallResponse;
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

@Tag(name = "Inventory Event Goodies",
        description = "What each of an event's goodies columns is, in stock terms")
@SecurityRequirement(name = "bearerAuth")
public interface InventoryGoodieMappingControllerApi {

    @Operation(
            summary = "List an event's goody links",
            description = """
                    Every goodies column of this event that has been pointed at an item, in name \
                    order. A column that has never been linked simply does not appear.

                    Nothing here is created by importing a file: the import stores a goodies \
                    column as a heading and a free-text cell and knows nothing about the \
                    catalogue, so these links are added afterwards, at the organizer's own pace."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Goody links retrieved successfully",
                    content = @Content(mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = InventoryGoodieMappingResponse.class)))),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Organization or event not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    ResponseEntity<List<InventoryGoodieMappingResponse>> listMappings(
            @PathVariable Long organizationId,
            @PathVariable Long eventId,
            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser);

    @Operation(
            summary = "Check what an event's goodies resolve to",
            description = """
                    **Roles:** `ROOT`, `ADMIN`, `ORGANIZER_ADMIN`, `ORGANIZER_USER`

                    The screen an organizer works from after linking. It lists every goody the \
                    event's roster carries, every distinct spelling under it, and how many \
                    participants are behind each one — 812 asked for `M`, 604 for `Large`, 41 for \
                    `xl` — with `handedOut`, how many of them have already been handed the goody.

                    Each spelling is read in a fixed order and never guessed. The item's own \
                    variant values come first, then its taught spellings, then the item itself when it \
                    varies by nothing and is handed over as it is. Whatever none of those \
                    recognise comes back as `UNRESOLVED`, and `unresolvedParticipants` says how \
                    many runners that leaves unaccounted for.

                    A goody nothing has been linked to still appears, so a heading typed \
                    differently from the column shows up as itself rather than as silence. The \
                    demand comes from the event's counters, so the roster is never walked; a file \
                    imported before those counters existed reads as empty until the event's \
                    statistics are reconciled."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Resolution retrieved successfully",
                    content = @Content(mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = InventoryGoodieResolutionResponse.class)))),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Organization or event not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/resolution")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    ResponseEntity<List<InventoryGoodieResolutionResponse>> resolveGoodies(
            @PathVariable Long organizationId,
            @PathVariable Long eventId,
            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser);

    @Operation(
            summary = "See what is needed against what is on the shelf",
            description = """
                    **Roles:** `ROOT`, `ADMIN`, `ORGANIZER_ADMIN`, `ORGANIZER_USER`

                    The shortfall report. For every goody it totals what the roster asks for, \
                    variant by variant, against what is on hand at the location that goody is \
                    handed out from — 1,900 mediums needed, 1,750 at the venue, 150 short.

                    The two halves arrive months apart and the report is useful with only the \
                    first. Demand can be totalled the day the roster is imported, before anything \
                    is ordered and before a location has been chosen, and that column on its own \
                    is the purchase order. `onHand` and `shortfall` fill in once a location is set \
                    and stock has arrived; until then they read zero and `shortfall` equals \
                    `needed`.

                    Demand is what the roster promised, never what turnout is expected to be. \
                    Every registered participant owed a goody is counted, because every one of \
                    them may walk in, until they are handed it: a hand-over takes its unit off the \
                    shelf, so from then on that participant leaves `needed` and counts in \
                    `handedOut` instead. During the expo `needed` is therefore what is still to \
                    hand out, and 340 mediums registered with 100 handed out and 240 on the shelf \
                    reads as 240 needed and none short. An event whose statistics were built \
                    before `handedOut` existed reads it wrongly until its statistics are \
                    reconciled. \
                    Spellings nothing recognises are held apart in \
                    `unresolvedParticipants` instead of being guessed into a variant, so they \
                    neither inflate a shortfall nor hide one — teach those spellings on the \
                    variant-aliases endpoint and they move into the rows here.

                    Several spellings routinely mean one variant, so `M`, `Medium` and `38` are \
                    totalled onto a single row. Each row also reports `elsewhere`, how many sit at \
                    the organization's other locations, because a shortfall covered from another \
                    shelf is a transfer rather than a purchase. Rows come worst shortfall first.

                    A goody nothing has been linked to appears with no rows, and so does a column \
                    that held too many distinct values to count one by one."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Shortfall retrieved successfully",
                    content = @Content(mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = InventoryGoodieShortfallResponse.class)))),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Organization or event not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/shortfall")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    ResponseEntity<List<InventoryGoodieShortfallResponse>> shortfall(
            @PathVariable Long organizationId,
            @PathVariable Long eventId,
            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser);

    @Operation(
            summary = "Link a goody to an item and a location",
            description = """
                    Points one goodies column at the item it is handed out from. The name must \
                    match the column heading the import stored, character for character, because \
                    that is the key a participant's entitlement is held under.

                    The item decides how a runner's cell value is read. An item that varies by \
                    nothing — a running bag, a medal — is handed over as it is, and the cell is \
                    ignored. An item that varies by one attribute, such as a t-shirt by size, uses \
                    the runner's own cell value to pick it. An item that varies by more than one \
                    attribute is refused: a single cell cannot say which combination a runner is \
                    owed.

                    The location is where the goody physically leaves from, and is the stock a \
                    handover will deduct. It can be left for later while the event is a draft — the \
                    item is known as soon as the roster is imported, the counter is often chosen \
                    days before the expo — but the event cannot be published until every link has \
                    one."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Goody linked successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = InventoryGoodieMappingResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request body",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Organization, event, item, or location not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "This goody is already linked, the item varies by more than one attribute, or the published event needs a location",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "The event is completed or cancelled, or its bill is final",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    ResponseEntity<InventoryGoodieMappingResponse> createMapping(
            @PathVariable Long organizationId,
            @PathVariable Long eventId,
            @Valid @RequestBody CreateInventoryGoodieMappingRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser);

    @Operation(
            summary = "Point a goody at a different item or location",
            description = """
                    Changes which item a goody comes out of, and which location it is handed out \
                    from. Both are replaced, not merged: omitting `locationId` clears the \
                    location, which a published event does not allow, so send the current value \
                    back when only the item is changing.

                    The goody name is the link's identity and is never changed here — to correct \
                    a heading, remove the link and add it again."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Goody link updated successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = InventoryGoodieMappingResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request body",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Organization, event, goody link, item, or location not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "The item varies by more than one attribute, or the published event needs a location",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "The event is completed or cancelled, or its bill is final",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/{mappingId}")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    ResponseEntity<InventoryGoodieMappingResponse> updateMapping(
            @PathVariable Long organizationId,
            @PathVariable Long eventId,
            @PathVariable Long mappingId,
            @Valid @RequestBody UpdateInventoryGoodieMappingRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser);

    @Operation(
            summary = "Unlink a goody",
            description = """
                    Removes the link. Nothing else is touched: the participants keep their \
                    entitlements and the item keeps its stock, the goody simply stops pointing \
                    anywhere."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Goody unlinked successfully"),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Organization, event, or goody link not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/{mappingId}")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    ResponseEntity<Void> deleteMapping(
            @PathVariable Long organizationId,
            @PathVariable Long eventId,
            @PathVariable Long mappingId,
            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser);
}
