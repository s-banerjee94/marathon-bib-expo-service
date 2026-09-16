package com.timekeeper.bibexpo.distribution.controller;

import com.timekeeper.bibexpo.distribution.model.dto.request.BulkCollectBibRequest;
import com.timekeeper.bibexpo.distribution.model.dto.request.BulkDistributeGoodiesRequest;
import com.timekeeper.bibexpo.distribution.model.dto.request.CollectBibRequest;
import com.timekeeper.bibexpo.distribution.model.dto.request.DistributeGoodiesRequest;
import com.timekeeper.bibexpo.distribution.model.dto.response.*;
import com.timekeeper.bibexpo.distribution.model.enums.LogSearchType;
import com.timekeeper.bibexpo.distribution.model.enums.PendingType;
import com.timekeeper.bibexpo.participant.model.dto.response.ParticipantDistributionResponse;
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
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Distribution Management", description = "APIs for managing bib and goodies distribution")
@SecurityRequirement(name = "bearerAuth")
@RequestMapping("/api/events/{eventId}/distribution")
public interface DistributionControllerApi {

    @PostMapping("/bib/{bibNumber}/collect")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER', 'ROLE_DISTRIBUTOR')")
    @Operation(
            summary = "Collect bib for a participant",
            description = """
                    **Roles:** `ROOT`, `ADMIN`, `ORGANIZER_ADMIN`, `ORGANIZER_USER`, `DISTRIBUTOR`

                    Record bib collection for a participant. \
                    Optionally specify who collected the bib (defaults to the participant when not provided). \
                    Optionally hand over goodies at the same time by listing them in goodiesItems. \
                    The staff member performing the collection is recorded automatically. \
                    A bib cannot be collected twice. \
                    Each goody must be on the participant's own list, or be one added to the event by hand, \
                    and cannot be handed over twice; when several goodies fail the same check, the error names \
                    them all. \
                    A goody added by hand whose inventory item comes in more than one variant needs the chosen \
                    variant in variantIds. \
                    A goody added by hand and linked to inventory takes one unit off its location's shelf, \
                    which may go below zero. \
                    If any goody is refused, nothing is saved: the bib stays uncollected."""
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Bib collected successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = BibDistributionResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "A goody added by hand needs its variant chosen, or the chosen variant is not its item's",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Participant not found, or goodies neither on their list nor added to the event "
                            + "by hand (all such goodies named in one message)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Bib already collected, or goodies already handed over (all such goodies named in "
                            + "one message)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Access forbidden - insufficient permissions",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<BibDistributionResponse> collectBib(
            @Parameter(description = "Event ID", example = "1")
            @PathVariable Long eventId,
            @Parameter(description = "Bib number", example = "3001")
            @PathVariable String bibNumber,
            @Parameter(description = "Collector details and goodies to hand over (optional - defaults to the participant)")
            @RequestBody(required = false) CollectBibRequest request,
            @AuthenticationPrincipal User currentUser);

    @PostMapping("/bib/{bibNumber}/undo")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @Operation(
            summary = "Undo bib collection for a participant",
            description = """
                    **Roles:** `ROOT`, `ADMIN`, `ORGANIZER_ADMIN`, `ORGANIZER_USER`

                    Undo bib collection and reset all distribution data. \
                    This resets bibCollectedAt, bibCollectedByName, bibCollectedByPhone and bibDistributedBy. \
                    IMPORTANT: this also resets ALL goodies distribution for this participant, \
                    and puts back at its location any stock those goodies took off the shelf. \
                    A bib that has not been collected cannot be undone."""
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Bib collection undone successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UndoDistributionResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Participant not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Bib has not been collected yet",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Access forbidden - insufficient permissions",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<UndoDistributionResponse> undoBib(
            @Parameter(description = "Event ID", example = "1")
            @PathVariable Long eventId,
            @Parameter(description = "Bib number", example = "3001")
            @PathVariable String bibNumber,
            @AuthenticationPrincipal User currentUser);

    @PostMapping("/goodies/{bibNumber}/distribute")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER', 'ROLE_DISTRIBUTOR')")
    @Operation(
            summary = "Distribute goodies items to a participant",
            description = """
                    **Roles:** `ROOT`, `ADMIN`, `ORGANIZER_ADMIN`, `ORGANIZER_USER`, `DISTRIBUTOR`

                    Hand one or more goodies to a participant in a single operation. \
                    The bib must already be collected. \
                    Each goody must be on the participant's own list, or be one added to the event by hand, \
                    and can only be handed over once; when several goodies fail the same check, the error names \
                    them all. \
                    A goody added by hand whose inventory item comes in more than one variant needs the chosen \
                    variant in variantIds; the counter gets the variants from the goodies list endpoint. \
                    A goody added by hand and linked to inventory takes one unit off its location's shelf, \
                    which may go below zero. \
                    If any goody is refused, none is recorded. \
                    The staff member performing the hand-over is recorded automatically."""
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Goodies items distributed successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = GoodiesDistributionResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "goodiesItems is empty, the bib has not been collected yet, or a goody added by hand "
                            + "needs its variant chosen",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Participant not found, or goodies neither on their list nor added to the event "
                            + "by hand (all such goodies named in one message)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Goodies already handed over (all such goodies named in one message)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Access forbidden - insufficient permissions",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<GoodiesDistributionResponse> distributeGoodies(
            @Parameter(description = "Event ID", example = "1")
            @PathVariable Long eventId,
            @Parameter(description = "Bib number", example = "3001")
            @PathVariable String bibNumber,
            @Parameter(description = "The goodies to hand over (at least one), and the variant chosen for any that "
                    + "needs one", required = true)
            @Valid @RequestBody DistributeGoodiesRequest request,
            @AuthenticationPrincipal User currentUser);

    @GetMapping("/goodies")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER', 'ROLE_DISTRIBUTOR')")
    @Operation(
            summary = "List the goodies the counter can hand out",
            description = """
                    **Roles:** `ROOT`, `ADMIN`, `ORGANIZER_ADMIN`, `ORGANIZER_USER`, `DISTRIBUTOR`

                    Every goody on the event's list, in list order, for the counter screen. \
                    A participant's own goodies come with their distribution status; this list adds the goodies \
                    added to the event by hand (source MANUAL), which can be handed to any participant. \
                    A goody linked to inventory names the item it comes out of. \
                    A MANUAL goody whose item comes in more than one variant lists the variants to choose between; \
                    send the chosen one in variantIds when handing it over."""
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "The event's goodies",
                    content = @Content(
                            mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = DistributionGoodieResponse.class))
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Event not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Access forbidden - insufficient permissions",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<List<DistributionGoodieResponse>> listGoodies(
            @Parameter(description = "Event ID", example = "1")
            @PathVariable Long eventId,
            @AuthenticationPrincipal User currentUser);

    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER', 'ROLE_DISTRIBUTOR')")
    @Operation(
            summary = "Get paginated list of participants who still have something to collect",
            description = """
                    **Roles:** `ROOT`, `ADMIN`, `ORGANIZER_ADMIN`, `ORGANIZER_USER`, `DISTRIBUTOR`

                    One page of the event's participants, in bib order, who still have something to collect. \
                    type=BIB lists the participants who have not collected their bib. \
                    type=GOODIES lists the participants who collected their bib but still have goodies of their own \
                    to collect; a goody added to the event by hand never makes anyone pending. \
                    pendingItems names each participant's own goodies still to hand over. \
                    totalPending gives the event-wide number still to collect, read from the event's statistics \
                    rather than by counting pages; it is filled for type=BIB, and null for type=GOODIES for now. \
                    Uses token-based pagination with limit and lastEvaluatedKey: every page but the last is full, \
                    and hasMore is true only when another pending participant exists."""
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "One page of participants who still have something to collect",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PendingParticipantListResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "type is missing or is neither BIB nor GOODIES, or limit is below 1",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Event not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Access forbidden - insufficient permissions",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<PendingParticipantListResponse> getPending(
            @Parameter(description = "Event ID", example = "1")
            @PathVariable Long eventId,
            @Parameter(description = "What is still to collect: BIB or GOODIES", required = true, example = "BIB")
            @RequestParam PendingType type,
            @Parameter(description = "Maximum number of items to return (default: 50, max: 100)", example = "50")
            @RequestParam(required = false) Integer limit,
            @Parameter(description = "Pagination token from previous response to get next page", example = "eyJldmVudElkIjp7IlMiOiIxIn0sImJpYk51bWJlciI6eyJTIjoiMzAwMSJ9fQ==")
            @RequestParam(required = false) String lastEvaluatedKey,
            @AuthenticationPrincipal User currentUser);

    @GetMapping("/logs")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @Operation(
            summary = "Get paginated distribution event logs for an event",
            description = """
                    **Roles:** `ROOT`, `ADMIN`, `ORGANIZER_ADMIN`, `ORGANIZER_USER`

                    Retrieve paginated distribution event logs for an event, newest first. \
                    Logs include bib collection, bib undo, goodies distribution, and goodies undo actions. \
                    Uses token-based pagination with limit and lastEvaluatedKey."""
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Paginated list of distribution event logs",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = DistributionLogListResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Event not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Access forbidden - insufficient permissions",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<DistributionLogListResponse> getDistributionLogs(
            @Parameter(description = "Event ID", example = "1")
            @PathVariable Long eventId,
            @Parameter(description = "Maximum number of items to return (default: 50, max: 100)", example = "50")
            @RequestParam(required = false) Integer limit,
            @Parameter(description = "Pagination token from previous response to get next page")
            @RequestParam(required = false) String lastEvaluatedKey,
            @AuthenticationPrincipal User currentUser);

    @GetMapping("/logs/{bibNumber}")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @Operation(
            summary = "Get distribution event logs for a specific participant",
            description = """
                    **Roles:** `ROOT`, `ADMIN`, `ORGANIZER_ADMIN`, `ORGANIZER_USER`

                    Retrieve all distribution event logs for a specific participant by bib number. \
                    Returns logs for bib collection, bib undo, goodies distribution, and goodies undo actions for this participant."""
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "List of distribution event logs for the participant",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = DistributionLogResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Event not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Access forbidden - insufficient permissions",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<List<DistributionLogResponse>> getParticipantLogs(
            @Parameter(description = "Event ID", example = "1")
            @PathVariable Long eventId,
            @Parameter(description = "Bib number", example = "3001")
            @PathVariable String bibNumber,
            @AuthenticationPrincipal User currentUser);

    @GetMapping("/{bibNumber}/status")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER', 'ROLE_DISTRIBUTOR')")
    @Operation(
            summary = "Get distribution status for a participant",
            description = """
                    **Roles:** `ROOT`, `ADMIN`, `ORGANIZER_ADMIN`, `ORGANIZER_USER`, `DISTRIBUTOR`

                    Retrieve the complete distribution status for a participant including bib collection and goodies distribution. \
                    Shows who collected the bib, which staff member distributed it, and the status of all goodies items."""
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Distribution status retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ParticipantDistributionResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Participant not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Access forbidden - insufficient permissions",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<ParticipantDistributionResponse> getDistributionStatus(
            @Parameter(description = "Event ID", example = "1")
            @PathVariable Long eventId,
            @Parameter(description = "Bib number", example = "3001")
            @PathVariable String bibNumber,
            @AuthenticationPrincipal User currentUser);

    @GetMapping("/logs/lookup")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @Operation(
            summary = "Lookup distribution logs using LSI (cost-efficient prefix search)",
            description = """
                    **Roles:** `ROOT`, `ADMIN`, `ORGANIZER_ADMIN`, `ORGANIZER_USER`

                    Efficient log lookup using DynamoDB Local Secondary Indexes (LSI). \
                    Uses Query with begins_with instead of Scan, resulting in lower cost and faster performance. \

                    **Search Types:** \
                    - `BIB`: Logs for a specific bib number (e.g., '3001') \
                    - `ACTION`: Logs by action type (e.g., 'BIB_COLLECTED', 'GOODIES_DISTRIBUTED') \
                    - `PERFORMED_BY`: Logs by staff member identifier (e.g., '123__|__john_doe') \
                    - `COLLECTOR`: Logs by collector name prefix (e.g., 'John') \
                    - `COLLECTOR_PHONE`: Logs by collector phone prefix (e.g., '+62') \

                    **Pagination:** Results are paginated using DynamoDB lastEvaluatedKey (base64 encoded)."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lookup completed successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = DistributionLogListResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid search parameters",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden - insufficient permissions",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Event not found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<DistributionLogListResponse> lookupLogs(
            @Parameter(description = "Event ID", required = true, example = "1")
            @PathVariable Long eventId,

            @Parameter(description = "Type of search: BIB, ACTION, PERFORMED_BY, COLLECTOR, COLLECTOR_PHONE", required = true,
                    schema = @Schema(implementation = LogSearchType.class))
            @RequestParam LogSearchType searchType,

            @Parameter(description = "Value to search for (uses begins_with prefix match)", required = true,
                    example = "BIB_COLLECTED")
            @RequestParam String searchValue,

            @Parameter(description = "Maximum number of results (default: 50, max: 100)", example = "50")
            @RequestParam(defaultValue = "50") Integer limit,

            @Parameter(description = "DynamoDB pagination key from previous response (base64 encoded)")
            @RequestParam(required = false) String lastEvaluatedKey,

            @AuthenticationPrincipal User currentUser
    );

    @PostMapping("/bib/bulk-collect")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER', 'ROLE_DISTRIBUTOR')")
    @Operation(
            summary = "Bulk collect bibs for multiple participants",
            description = """
                    **Roles:** `ROOT`, `ADMIN`, `ORGANIZER_ADMIN`, `ORGANIZER_USER`, `DISTRIBUTOR`

                    Collect the bibs of up to 25 participants in one request, each optionally with goodies handed \
                    over at the same time. \
                    Each entry in items works exactly like a single bib collect: goodiesItems (optional) may hold any \
                    of the participant's own goodies or any goody added to the event by hand, and variantIds picks \
                    the variant for a goody added by hand whose inventory item comes in more than one. \
                    collectorName and collectorPhone, when given, are recorded for every bib; otherwise each \
                    participant is their own collector. \
                    The staff member performing the collection is recorded automatically. \
                    Entries are processed one by one and independently: one that fails does not stop the others, \
                    and a failed entry saves nothing, neither its bib nor its goodies. \
                    When several goodies of one entry fail the same check, its reason names them all. \
                    The response lists the bibs collected and, for each failure, the bib, the goodies it asked for, \
                    and the reason. \
                    A request with no entries, more than 25 entries, or an entry without a bib number is refused \
                    with 400 before anything is saved."""
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Bulk collection completed (check successful and failed for each entry)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = BulkDistributionResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "items is missing or empty, holds more than 25 entries, or an entry has no bib number",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Event not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Access forbidden - insufficient permissions",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<BulkDistributionResponse> bulkCollectBib(
            @Parameter(description = "Event ID", example = "1")
            @PathVariable Long eventId,
            @Parameter(description = "The bibs to collect (at most 25), each with any goodies, and the collector details",
                    required = true)
            @Valid @RequestBody BulkCollectBibRequest request,
            @AuthenticationPrincipal User currentUser);

    @PostMapping("/goodies/bulk-distribute")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER', 'ROLE_DISTRIBUTOR')")
    @Operation(
            summary = "Bulk distribute goodies items to multiple participants",
            description = """
                    **Roles:** `ROOT`, `ADMIN`, `ORGANIZER_ADMIN`, `ORGANIZER_USER`, `DISTRIBUTOR`

                    Hand goodies to up to 25 participants in one request, each with their own goodies. \
                    Each entry in items works exactly like a single goodies hand-over: the participant's bib must \
                    already be collected, goodiesItems may hold any of their own goodies or any goody added to the \
                    event by hand, each handed over once, and variantIds picks the variant for a goody added by hand \
                    whose inventory item comes in more than one. \
                    The staff member performing the hand-over is recorded automatically. \
                    Entries are processed one by one and independently: one that fails does not stop the others, \
                    and a failed entry records none of its goodies. \
                    When several goodies of one entry fail the same check, its reason names them all. \
                    The response lists each entry served as bib:goody,goody and, for each failure, the bib, its \
                    goodies, and the reason. \
                    A request with no entries, more than 25 entries, or an entry without a bib number or goodies is \
                    refused with 400 before anything is saved."""
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Bulk distribution completed (check successful and failed for each entry)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = BulkDistributionResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "items is missing or empty, holds more than 25 entries, or an entry has no bib number "
                            + "or no goodies",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Event not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Access forbidden - insufficient permissions",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<BulkDistributionResponse> bulkDistributeGoodies(
            @Parameter(description = "Event ID", example = "1")
            @PathVariable Long eventId,
            @Parameter(description = "The participants to hand goodies to (at most 25), each with their own goodies",
                    required = true)
            @Valid @RequestBody BulkDistributeGoodiesRequest request,
            @AuthenticationPrincipal User currentUser);
}
