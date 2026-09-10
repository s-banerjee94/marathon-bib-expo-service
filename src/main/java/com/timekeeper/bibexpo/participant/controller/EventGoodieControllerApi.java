package com.timekeeper.bibexpo.participant.controller;

import com.timekeeper.bibexpo.event.model.dto.response.EventGoodieResponse;
import com.timekeeper.bibexpo.participant.model.dto.request.AddEventGoodieRequest;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Tag(name = "Event Goodies", description = "The goodies an event hands out, imported or added by hand")
@SecurityRequirement(name = "bearerAuth")
public interface EventGoodieControllerApi {

    @Operation(
            summary = "List an event's goodies",
            description = """
                    Every goody the event hands out, in the order it was added. `source` says how \
                    each one got there: `IMPORT` when an imported file brought it, so participant \
                    records carry it under that name, or `MANUAL` when an organizer added it by \
                    hand. A goody added by hand is owed to every participant, though no record \
                    carries it."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Goodies retrieved successfully",
                    content = @Content(mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = EventGoodieResponse.class)))),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Event not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{eventId}/goodies")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    ResponseEntity<List<EventGoodieResponse>> listGoodies(
            @PathVariable Long eventId,
            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser);

    @Operation(
            summary = "Add a goody by hand",
            description = """
                    Adds a goody every participant is owed that no imported file mentioned — a \
                    sponsor's kit that arrives after the roster is in. The name is compared with \
                    the list ignoring case and surrounding spaces, so `Sipper` and ` sipper ` are \
                    the same goody.

                    Allowed while the event is a draft or published, but not once it is completed \
                    or cancelled, or its bill is final. The event's plan caps how many goodies it \
                    can hold, imported ones included."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Goody added successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = EventGoodieResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request body",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Event not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "The event already has this goody, or holds as many as its plan allows",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "The event is completed or cancelled, or its bill is final",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{eventId}/goodies")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    ResponseEntity<EventGoodieResponse> addGoodie(
            @PathVariable Long eventId,
            @Valid @RequestBody AddEventGoodieRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser);

    @Operation(
            summary = "Remove a goody",
            description = """
                    Removes a goody from the event's list. The name is matched ignoring case and \
                    surrounding spaces.

                    A goody added by hand is only on the list, so removing it touches nothing else, \
                    and it is allowed while the event is a draft or published. An imported goody is \
                    also carried by participant records, so removing it takes it out of every record \
                    that has it and rebuilds the event's counters; because that rewrites the roster, \
                    it is allowed only while the event is a draft.

                    Removal is refused once the goody has been handed out to anyone, while it is \
                    still linked to an inventory item, and once the event is completed or cancelled \
                    or its bill is final."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Goody removed successfully"),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Event or goody not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "The goody has been handed out, or is still linked to an inventory item",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "The event's status or bill does not allow this removal",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/{eventId}/goodies")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    ResponseEntity<Void> removeGoodie(
            @PathVariable Long eventId,
            @Parameter(description = "The goody name, matched ignoring case and surrounding spaces",
                    example = "Sponsor Kit", required = true)
            @RequestParam String name,
            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser);
}
