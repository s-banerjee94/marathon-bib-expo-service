package com.timekeeper.bibexpo.controller;

import com.timekeeper.bibexpo.exception.ErrorResponse;
import com.timekeeper.bibexpo.model.dto.request.BulkCollectBibRequest;
import com.timekeeper.bibexpo.model.dto.request.BulkDistributeGoodiesRequest;
import com.timekeeper.bibexpo.model.dto.request.CollectBibRequest;
import com.timekeeper.bibexpo.model.dto.request.DistributeGoodiesRequest;
import com.timekeeper.bibexpo.model.dto.response.BibDistributionResponse;
import com.timekeeper.bibexpo.model.dto.response.DistributionLogListResponse;
import com.timekeeper.bibexpo.model.enums.LogSearchType;
import com.timekeeper.bibexpo.model.dto.response.BulkDistributionResponse;
import com.timekeeper.bibexpo.model.dto.response.DistributionLogResponse;
import com.timekeeper.bibexpo.model.dto.response.GoodiesDistributionResponse;
import com.timekeeper.bibexpo.model.dto.response.ParticipantDistributionResponse;
import com.timekeeper.bibexpo.model.dto.response.PendingBibListResponse;
import com.timekeeper.bibexpo.model.dto.response.PendingGoodiesListResponse;
import com.timekeeper.bibexpo.model.dto.response.UndoDistributionResponse;
import com.timekeeper.bibexpo.model.entity.User;

import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "Distribution Management", description = "APIs for managing bib and goodies distribution")
@SecurityRequirement(name = "bearerAuth")
@RequestMapping("/api/events/{eventId}/distribution")
public interface DistributionControllerApi {

    @PostMapping("/bib/{bibNumber}/collect")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER', 'ROLE_DISTRIBUTOR')")
    @Operation(
            summary = "Collect bib for a participant",
            description = """
                    Record bib collection for a participant. \
                    Optionally specify who collected the bib (defaults to participant if not provided). \
                    Optionally distribute goodies items at the same time by providing a list of item names. \
                    Staff member performing the distribution is automatically recorded. \
                    Cannot collect the same bib twice. \
                    Goodies items must exist in participant's goodies allocation and cannot be distributed twice."""
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
                    responseCode = "404",
                    description = "Participant not found or goodies item not found in participant's allocation",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Bib already collected or goodies item already distributed",
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
            @Parameter(description = "Collector details (optional - defaults to participant)")
            @RequestBody(required = false) CollectBibRequest request,
            @AuthenticationPrincipal User currentUser);

    @PostMapping("/bib/{bibNumber}/undo")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @Operation(
            summary = "Undo bib collection for a participant",
            description = """
                    Undo bib collection and reset all distribution data. \
                    This will reset: bibCollectedAt, bibCollectedByName, bibCollectedByPhone, bibDistributedBy. \
                    IMPORTANT: This will also reset ALL goodies distribution for this participant. \
                    Only accessible by ROOT, ADMIN, ORGANIZER_ADMIN, ORGANIZER_USER (NOT DISTRIBUTOR). \
                    Cannot undo if bib has not been collected."""
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
                    description = "Access forbidden - only ROOT, ADMIN, ORGANIZER_ADMIN, ORGANIZER_USER can undo",
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
            summary = "Distribute a goodies item to a participant",
            description = """
                    Distribute a specific goodies item to a participant. \
                    Requires that the bib has been collected first. \
                    The goodies item must exist in the participant's goodies allocation. \
                    Each goodies item can only be distributed once. \
                    Staff member performing the distribution is automatically recorded."""
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Goodies item distributed successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = GoodiesDistributionResponse.class)
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
                    responseCode = "404",
                    description = "Participant not found or goodies item not found in participant's allocation",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Goodies item already distributed",
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
            @Parameter(description = "Goodies distribution request with item name", required = true)
            @RequestBody DistributeGoodiesRequest request,
            @AuthenticationPrincipal User currentUser);

    @GetMapping("/pending/bib")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER', 'ROLE_DISTRIBUTOR')")
    @Operation(
            summary = "Get paginated list of participants with pending bib collection",
            description = """
                    Retrieve participants for an event who have not yet collected their bibs with pagination support. \
                    Returns participant details including bib number, name, contact info, and race/category information. \
                    Uses token-based pagination with limit and lastEvaluatedKey for efficient DynamoDB querying."""
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Paginated list of participants with pending bib collection",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PendingBibListResponse.class)
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
    ResponseEntity<PendingBibListResponse> getPendingBibs(
            @Parameter(description = "Event ID", example = "1")
            @PathVariable Long eventId,
            @Parameter(description = "Maximum number of items to return (default: 50, max: 100)", example = "50")
            @RequestParam(required = false) Integer limit,
            @Parameter(description = "Pagination token from previous response to get next page", example = "eyJldmVudElkIjp7IlMiOiIxIn0sImJpYk51bWJlciI6eyJTIjoiMzAwMSJ9fQ==")
            @RequestParam(required = false) String lastEvaluatedKey,
            @AuthenticationPrincipal User currentUser);

    @GetMapping("/logs")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN')")
    @Operation(
            summary = "Get paginated distribution event logs for an event",
            description = """
                    Retrieve paginated distribution event logs for an event. \
                    Logs include bib collection, bib undo, goodies distribution, and goodies undo actions. \
                    Uses token-based pagination with limit and lastEvaluatedKey for efficient DynamoDB querying. \
                    Only accessible by ROOT, ADMIN, and ORGANIZER_ADMIN roles."""
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
                    description = "Access forbidden - only ROOT, ADMIN, ORGANIZER_ADMIN can access logs",
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
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN')")
    @Operation(
            summary = "Get distribution event logs for a specific participant",
            description = """
                    Retrieve all distribution event logs for a specific participant by bib number. \
                    Returns logs for bib collection, bib undo, goodies distribution, and goodies undo actions for this participant. \
                    Only accessible by ROOT, ADMIN, and ORGANIZER_ADMIN roles."""
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
                    description = "Access forbidden - only ROOT, ADMIN, ORGANIZER_ADMIN can access logs",
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

    @GetMapping("/pending/goodies")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER', 'ROLE_DISTRIBUTOR')")
    @Operation(
            summary = "Get paginated list of participants with pending goodies",
            description = """
                    Retrieve participants who have collected their bibs but still have pending goodies items to collect. \
                    Returns participant details including which goodies items are still pending. \
                    Uses token-based pagination with limit and lastEvaluatedKey for efficient DynamoDB querying."""
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Paginated list of participants with pending goodies",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PendingGoodiesListResponse.class)
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
    ResponseEntity<PendingGoodiesListResponse> getPendingGoodies(
            @Parameter(description = "Event ID", example = "1")
            @PathVariable Long eventId,
            @Parameter(description = "Maximum number of items to return (default: 50, max: 100)", example = "50")
            @RequestParam(required = false) Integer limit,
            @Parameter(description = "Pagination token from previous response to get next page", example = "eyJldmVudElkIjp7IlMiOiIxIn0sImJpYk51bWJlciI6eyJTIjoiMzAwMSJ9fQ==")
            @RequestParam(required = false) String lastEvaluatedKey,
            @AuthenticationPrincipal User currentUser);

    @GetMapping("/logs/lookup")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN')")
    @Operation(
            summary = "Lookup distribution logs using LSI (cost-efficient prefix search)",
            description = """
                    Efficient log lookup using DynamoDB Local Secondary Indexes (LSI). \
                    Uses Query with begins_with instead of Scan, resulting in lower cost and faster performance. \

                    **Search Types:** \
                    - `BIB`: Logs for a specific bib number (e.g., '3001') \
                    - `ACTION`: Logs by action type (e.g., 'BIB_COLLECTED', 'GOODIES_DISTRIBUTED') \
                    - `PERFORMED_BY`: Logs by staff member identifier (e.g., '123__|__john_doe') \
                    - `COLLECTOR`: Logs by collector name prefix (e.g., 'John') \
                    - `ITEM`: Logs for a specific goodies item prefix (e.g., 'T-Shirt') \

                    **Pagination:** Results are paginated using DynamoDB lastEvaluatedKey (base64 encoded)."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lookup completed successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = DistributionLogListResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid search parameters",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Event not found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<DistributionLogListResponse> lookupLogs(
            @Parameter(description = "Event ID", required = true, example = "1")
            @PathVariable Long eventId,

            @Parameter(description = "Type of search: BIB, ACTION, PERFORMED_BY, COLLECTOR, ITEM", required = true,
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
                    Collect bibs for multiple participants at once with the same collector information. \
                    All bibs will be recorded as collected by the same person (or each participant if collector info not provided). \
                    Staff member performing the distribution is automatically recorded. \
                    Returns counts of successful and failed operations with detailed failure reasons."""
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Bulk collection completed (check response for individual results)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = BulkDistributionResponse.class)
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
            @Parameter(description = "Bulk collection request with bib numbers and collector details", required = true)
            @RequestBody BulkCollectBibRequest request,
            @AuthenticationPrincipal User currentUser);

    @PostMapping("/goodies/bulk-distribute")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER', 'ROLE_DISTRIBUTOR')")
    @Operation(
            summary = "Bulk distribute goodies items to multiple participants",
            description = """
                    Distribute goodies items to multiple participants at once. \
                    Each item in the request can be for a different participant and different goodies item. \
                    Requires that bibs have been collected first for all participants. \
                    Returns counts of successful and failed operations with detailed failure reasons."""
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Bulk distribution completed (check response for individual results)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = BulkDistributionResponse.class)
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
            @Parameter(description = "Bulk distribution request with list of bib numbers and item names", required = true)
            @RequestBody BulkDistributeGoodiesRequest request,
            @AuthenticationPrincipal User currentUser);
}
