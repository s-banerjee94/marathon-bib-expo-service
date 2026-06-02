package com.timekeeper.bibexpo.controller;

import com.timekeeper.bibexpo.exception.ErrorResponse;
import com.timekeeper.bibexpo.model.dto.request.BulkDeleteParticipantsRequest;
import com.timekeeper.bibexpo.model.dto.request.CreateParticipantRequest;
import com.timekeeper.bibexpo.model.dto.request.UpdateParticipantRequest;
import com.timekeeper.bibexpo.model.dto.response.*;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.enums.ExportField;
import com.timekeeper.bibexpo.model.enums.SearchType;
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
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "Participant Management", description = "APIs for managing event participants and CSV imports")
@SecurityRequirement(name = "bearerAuth")
public interface ParticipantControllerApi {

    @PostMapping("/{eventId}/participants")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @Operation(
            summary = "Create a new participant",
            description = """
                    Create a single participant for an event. \
                    Adding is allowed while the event is in draft or published; it is blocked once the event is completed or cancelled. \

                    **Required:** bibNumber, fullName, raceId, categoryId, gender. \

                    **Conditionally required:** at least one of phoneNumber / email, and at least one of dateOfBirth / age. \

                    **Optional:** chipNumber, country, city, bibCollectedAt, goodies, additionalFields, emergencyContactName, emergencyContactPhone, notes. \

                    **additionalFields:** free-form key-value columns, matching the dynamic columns retained from a CSV import. \

                    **Uniqueness:** bibNumber must be unique within the event; chipNumber, when provided, must also be unique within the event (blank or omitted chip numbers are exempt). \

                    **Normalization:** fullName is stored and returned in UPPERCASE; email in lowercase."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Participant created successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ParticipantResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request data, participant with BIB number already exists, or the event is completed or cancelled",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden - user not authorized for this event",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Event, race, or category not found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Chip number already assigned to another participant in this event",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<ParticipantResponse> createParticipant(
            @Parameter(description = "Event ID", required = true)
            @PathVariable Long eventId,

            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Participant data",
                    required = true,
                    content = @Content(schema = @Schema(implementation = CreateParticipantRequest.class)))
            @jakarta.validation.Valid @org.springframework.web.bind.annotation.RequestBody CreateParticipantRequest request,

            @AuthenticationPrincipal User currentUser
    );

    @GetMapping("/{eventId}/participants")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @Operation(
            summary = "Get all participants for an event",
            description = """
                    Retrieve paginated list of participants for a specific event. \
                    Supports DynamoDB-style pagination with lastEvaluatedKey (base64 encoded). \
                    Default limit is 50, maximum is 100."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Participants retrieved successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ParticipantListResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden"),
            @ApiResponse(responseCode = "404", description = "Event not found")
    })
    ResponseEntity<ParticipantListResponse> getParticipants(
            @Parameter(description = "Event ID", required = true)
            @PathVariable Long eventId,

            @Parameter(description = "Maximum number of participants to return (default: 50, max: 100)")
            @RequestParam(defaultValue = "50") Integer limit,

            @Parameter(description = "DynamoDB pagination key from previous response (base64 encoded)")
            @RequestParam(required = false) String lastEvaluatedKey,

            @AuthenticationPrincipal User currentUser
    );

    @GetMapping("/{eventId}/participants/count")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @Operation(
            summary = "Get participant count for an event",
            description = "Returns the total number of participants registered for an event as a simple count object {count: number}"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Count retrieved successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(example = "{\"count\": 150}"))),
            @ApiResponse(responseCode = "403", description = "Access forbidden - user not authorized for this event",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Event not found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<Map<String, Long>> getParticipantCount(
            @Parameter(description = "Event ID", required = true)
            @PathVariable Long eventId,

            @AuthenticationPrincipal User currentUser
    );

    @GetMapping("/{eventId}/participants/search")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @Operation(
            summary = "Search and filter participants",
            description = """
                    Flexible search for participants using a search term and/or optional filters. \

                    **Search Logic:** If searchTerm is provided, it matches ANY of: fullName, email, phoneNumber, or chipNumber \
                    (case-insensitive, partial match). If no searchTerm is provided, filters alone are applied. \

                    **Filter Logic:** All specified filters (race, category, gender, age range, city, country) must match (AND logic). \

                    **Pagination:** Results are paginated using DynamoDB-style pagination with lastEvaluatedKey (base64 encoded). \

                    **Performance Note:** ⚠️ Uses DynamoDB Scan operation which may be slower for large datasets. \
                    For cost-efficient lookups by specific fields, use the /lookup endpoint instead."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Search completed successfully. Returns matching participants with pagination info.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ParticipantListResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid search parameters - search term too short (<2 chars), invalid age range (min > max), limit > 100, or invalid pagination key",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden - user not authorized for this event",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Event not found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<ParticipantListResponse> searchParticipants(
            @Parameter(description = "Event ID", required = true, example = "1")
            @PathVariable Long eventId,

            @Parameter(description = "Search term (min 2 chars) - searches across name, email, phone, chip number",
                    example = "SANJAY")
            @RequestParam(required = false) String searchTerm,

            @Parameter(description = "Filter by race ID", example = "5")
            @RequestParam(required = false) String raceId,

            @Parameter(description = "Filter by category ID", example = "12")
            @RequestParam(required = false) String categoryId,

            @Parameter(description = "Filter by gender (M/F/O)", example = "M")
            @RequestParam(required = false) String gender,

            @Parameter(description = "Filter by minimum age", example = "18")
            @RequestParam(required = false) Integer minAge,

            @Parameter(description = "Filter by maximum age", example = "60")
            @RequestParam(required = false) Integer maxAge,

            @Parameter(description = "Filter by city (partial match)", example = "Mumbai")
            @RequestParam(required = false) String city,

            @Parameter(description = "Filter by country (partial match)", example = "India")
            @RequestParam(required = false) String country,

            @Parameter(description = "Maximum number of results (default: 50, max: 100)", example = "50")
            @RequestParam(defaultValue = "50") Integer limit,

            @Parameter(description = "DynamoDB pagination key from previous response (base64 encoded)")
            @RequestParam(required = false) String lastEvaluatedKey,

            @AuthenticationPrincipal User currentUser
    );

    @GetMapping("/{eventId}/participants/lookup")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER', 'ROLE_DISTRIBUTOR')")
    @Operation(
            summary = "Lookup participants using LSI (cost-efficient alternative to search)",
            description = """
                    Efficient participant lookup using DynamoDB Local Secondary Indexes (LSI). \
                    **Recommended over /search endpoint** - Uses Query operations instead of Scan, resulting in lower cost and faster performance. \

                    **Search Types and Matching Logic:** \
                    - `NAME`: Search by full name (begins_with, case-insensitive). Example: 'JO' matches 'John', 'Joanna' \
                    - `EMAIL`: Search by email address (begins_with, case-insensitive). Example: 'john' matches 'john@example.com', 'johnny@example.com' \
                    - `PHONE`: Search by phone number (begins_with, exact prefix match). Example: '+91' matches '+91-9876543210' \
                    - `BIB`: Exact match lookup by bib number (most efficient). Example: 'BIB001' returns exactly one participant or none \
                    - `RACE`: searchValue is a raceId. Returns ALL participants in that race (begins_with on the raceId# composite key). Example: '14'. Supports pagination. \
                    - `CATEGORY`: searchValue is the composite 'raceId#categoryId' (exact match). Returns participants in that one race+category. Example: '14#30' (URL-encode # as %23). Supports pagination. \

                    **Pagination:** Results are paginated using DynamoDB lastEvaluatedKey (base64 encoded). \
                    RACE and CATEGORY searches may return large result sets and benefit from pagination."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lookup completed successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ParticipantListResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid search parameters",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden - user does not belong to the event's organization or event is disabled",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Event not found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<ParticipantListResponse> lookupParticipants(
            @Parameter(description = "Event ID", required = true, example = "1")
            @PathVariable Long eventId,

            @Parameter(description = "Type of search: NAME, EMAIL, PHONE, BIB, RACE, CATEGORY", required = true,
                    schema = @Schema(implementation = SearchType.class))
            @RequestParam SearchType searchType,

            @Parameter(description = "Value to search for. NAME/EMAIL/PHONE use begins_with on the LSI. "
                    + "RACE expects a raceId and returns every participant in that race. "
                    + "CATEGORY expects the composite \"raceId#categoryId\" and matches that exact category.",
                    required = true, example = "JOHN")
            @RequestParam String searchValue,

            @Parameter(description = "Maximum number of results (default: 50, max: 100)", example = "50")
            @RequestParam(defaultValue = "50") Integer limit,

            @Parameter(description = "DynamoDB pagination key from previous response (base64 encoded)")
            @RequestParam(required = false) String lastEvaluatedKey,

            @AuthenticationPrincipal User currentUser
    );

    @GetMapping(value = "/{eventId}/participants/export", produces = "text/csv")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @Operation(
            summary = "Export participants to CSV",
            description = """
                    Export all participants for an event to CSV format. \
                    User can select which fields to include in the export using the 'fields' parameter. \
                    If no fields are specified, all fields are exported. \
                    Goodies columns are dynamically generated based on participant data. \

                    **Available fields:** BIB_NUMBER, CHIP_NUMBER, FULL_NAME, EMAIL, PHONE_NUMBER, \
                    DATE_OF_BIRTH, AGE, GENDER, COUNTRY, CITY, RACE_NAME, CATEGORY_NAME, \
                    BIB_COLLECTED_AT, EMERGENCY_CONTACT_NAME, EMERGENCY_CONTACT_PHONE, NOTES, GOODIES"""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "CSV file generated successfully",
                    content = @Content(mediaType = "text/csv")),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Event not found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<byte[]> exportParticipants(
            @Parameter(description = "Event ID", required = true, example = "1")
            @PathVariable Long eventId,

            @Parameter(description = "Fields to include in export. If empty, all fields are exported.",
                    schema = @Schema(implementation = ExportField.class))
            @RequestParam(required = false) List<ExportField> fields,

            @AuthenticationPrincipal User currentUser
    );

    @GetMapping("/{eventId}/participants/statistics")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @Operation(
            summary = "Get participant statistics for an event",
            description = """
                    Returns aggregated participant statistics for the event, computed live from current data. \

                    **Response includes:** \
                    - totalParticipants: Total number of registered participants \
                    - bibCollectedCount: Number of participants who collected their BIB \
                    - pendingCount: Number of participants pending BIB collection \
                    - raceBreakdown: Counts per race (raceId, raceName, count, bibCollectedCount) \
                    - categoryBreakdown: Counts per category (categoryId, categoryName, count) \
                    - genderBreakdown: Counts by gender (male, female, other)"""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Statistics retrieved successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ParticipantStatisticsResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Event not found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<ParticipantStatisticsResponse> getParticipantStatistics(
            @Parameter(description = "Event ID", required = true, example = "1")
            @PathVariable Long eventId,

            @AuthenticationPrincipal User currentUser
    );

    @PostMapping("/{eventId}/participants/statistics/reconcile")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @Operation(
            summary = "Rebuild participant statistics counters from source-of-truth data",
            description = """
                    Wipes the pre-aggregated statistics counters for the event and rebuilds them by querying \
                    all participant rows and aggregating in memory. \

                    **Use cases:** \
                    - Initial backfill for an existing event before the counter-based statistics endpoint is used \
                    - Drift recovery if counter updates were missed due to transient DynamoDB failures \
                    - Post-batch-import refresh (also invoked automatically by the batch import job) \

                    Returns the freshly computed statistics so the caller does not need to issue a follow-up \
                    GET request."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Counters rebuilt successfully and freshly computed statistics returned",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ParticipantStatisticsResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Event not found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<ParticipantStatisticsResponse> reconcileParticipantStatistics(
            @Parameter(description = "Event ID", required = true, example = "1")
            @PathVariable Long eventId,

            @AuthenticationPrincipal User currentUser
    );

    @DeleteMapping("/{eventId}/participants")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @Operation(
            summary = "Delete all participants for an event",
            description = """
                    ⚠️ WARNING: This will permanently delete ALL participants for the specified event. \
                    This action cannot be undone. Use with caution. \
                    Allowed only while the event is in DRAFT; deletion is blocked once the event is published, completed, or cancelled. \

                    Attempts to delete all participants and returns counts of successful and failed deletions."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Deletion operation completed. Response includes deleted and failed counts.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = DeleteParticipantsResponse.class))),
            @ApiResponse(responseCode = "400", description = "The event is not in draft - participants can only be deleted while the event is in draft",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden - user not authorized for this event",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Event not found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<DeleteParticipantsResponse> deleteAllParticipants(
            @Parameter(description = "Event ID", required = true)
            @PathVariable Long eventId,
            @AuthenticationPrincipal User currentUser
    );

    @DeleteMapping("/{eventId}/participants/bulk")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @Operation(
            summary = "Delete specific participants by bib numbers",
            description = """
                    Delete multiple participants in a single batch operation. \
                    **Constraints:** \
                    - Minimum 1 bib number required (empty list will be rejected) \
                    - Maximum 25 participants per request (more will be rejected) \
                    - Allowed only while the event is in DRAFT; deletion is blocked once the event is published, completed, or cancelled \

                    Returns counts of successfully deleted participants and any failures that occurred during the operation."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Bulk delete operation completed. Response includes deleted and failed counts.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = DeleteParticipantsResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request - empty list, more than 25 items, invalid bib numbers, or the event is not in draft",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden - user not authorized for this event",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Event not found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<DeleteParticipantsResponse> deleteBulkParticipants(
            @Parameter(description = "Event ID", required = true)
            @PathVariable Long eventId,

            @Parameter(description = "Request body containing list of bib numbers to delete", required = true)
            @RequestBody @jakarta.validation.Valid BulkDeleteParticipantsRequest request,

            @AuthenticationPrincipal User currentUser
    );

    @GetMapping("/{eventId}/participants/{bibNumber}")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @Operation(
            summary = "Get participant by bib number",
            description = """
                    Retrieve complete details of a specific participant using their bib number. \
                    Returns all participant information including goodies, contact details, and collection status. \
                    **Note:** BIB numbers are unique within an event."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Participant found and returned",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ParticipantResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden - user not authorized for this event",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Participant with specified bib number or event not found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<ParticipantResponse> getParticipantByBibNumber(
            @Parameter(description = "Event ID", required = true)
            @PathVariable Long eventId,

            @Parameter(description = "Bib number", required = true)
            @PathVariable String bibNumber,

            @AuthenticationPrincipal User currentUser
    );

    @PatchMapping("/{eventId}/participants/{bibNumber}")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @Operation(
            summary = "Update participant details",
            description = """
                    Update participant information with partial (PATCH) semantics. Only fields present in the request are applied. \
                    Editing is allowed while the event is in draft or published; it is blocked once the event is completed or cancelled. \

                    **What Can Be Updated:** \
                    - Identity & contact: chipNumber, fullName, email, phoneNumber \
                    - Demographics: dateOfBirth, age, gender \
                    - Location: country, city \
                    - Race & Category: validated against the event's races and categories \
                    - BIB Collection: bibCollectedAt \
                    - Emergency Contact: emergencyContactName, emergencyContactPhone \
                    - Notes \
                    - additionalFields: free-form columns, merged (see below) \
                    - BIB Number: via the separate `newBibNumber` field; because bibNumber is the DynamoDB sort key the record is deleted and recreated under the new number \

                    **What Cannot Be Updated here:** \
                    - Goodies allocation: fixed at creation/import. Only the handout status changes, through the distribution endpoints. \

                    **additionalFields - partial merge (how to build the request):** \
                    - Send only the keys you want to change inside the additionalFields object. \
                    - Add or update a key: include it with a non-empty value, e.g. {"tShirtSize": "M"}. \
                    - Remove a key: include it with a null (or empty) value, e.g. {"oldColumn": null}. \
                    - Keys you do not include are left unchanged; omit additionalFields entirely to leave all extra columns untouched. \

                    **Uniqueness:** chipNumber, when changed to a non-blank value, must be unique within the event; updating it to a chip number already held by another participant is rejected. \

                    **Normalization:** When updated, fullName is stored in UPPERCASE and email in lowercase. \

                    **Behavior:** A null or omitted top-level field is ignored and won't overwrite existing values; this applies to additionalFields as a whole, while a null value for an individual key inside it removes that key."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Participant updated successfully. Returns the complete updated record.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ParticipantResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request data - invalid race/category ID, invalid field values, field validation errors, or the event is completed or cancelled",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access denied - user not authorized for this event",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Event, participant, race, or category not found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Chip number already assigned to another participant in this event",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<ParticipantResponse> updateParticipant(
            @Parameter(description = "Event ID", required = true, example = "1")
            @PathVariable Long eventId,

            @Parameter(description = "Participant BIB number", required = true, example = "21001")
            @PathVariable String bibNumber,

            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Participant update data. Only non-null fields will be updated.",
                    required = true,
                    content = @Content(schema = @Schema(implementation = UpdateParticipantRequest.class)))
            @jakarta.validation.Valid @org.springframework.web.bind.annotation.RequestBody UpdateParticipantRequest request,

            @AuthenticationPrincipal User currentUser
    );

    @DeleteMapping("/{eventId}/participants/{bibNumber}")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @Operation(
            summary = "Delete a participant by bib number",
            description = """
                    Permanently delete a single participant from an event using their bib number. \
                    This action cannot be undone. The participant record will be completely removed from DynamoDB. \
                    Allowed only while the event is in DRAFT; deletion is blocked once the event is published, completed, or cancelled."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Participant deleted successfully. Response includes deletion count and status.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = DeleteParticipantsResponse.class))),
            @ApiResponse(responseCode = "400", description = "The event is not in draft - participants can only be deleted while the event is in draft",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden - user not authorized for this event",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Event or participant with specified bib number not found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<DeleteParticipantsResponse> deleteParticipant(
            @Parameter(description = "Event ID", required = true)
            @PathVariable Long eventId,

            @Parameter(description = "Bib number of the participant to delete", required = true)
            @PathVariable String bibNumber,

            @AuthenticationPrincipal User currentUser
    );
}
