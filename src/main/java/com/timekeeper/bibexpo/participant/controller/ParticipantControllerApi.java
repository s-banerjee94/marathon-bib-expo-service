package com.timekeeper.bibexpo.participant.controller;

import com.timekeeper.bibexpo.participant.model.dto.request.BulkDeleteParticipantsRequest;
import com.timekeeper.bibexpo.participant.model.dto.request.CreateParticipantRequest;
import com.timekeeper.bibexpo.participant.model.dto.request.UpdateParticipantRequest;
import com.timekeeper.bibexpo.participant.model.dto.response.DeleteParticipantsResponse;
import com.timekeeper.bibexpo.participant.model.dto.response.ParticipantListResponse;
import com.timekeeper.bibexpo.participant.model.dto.response.ParticipantResponse;
import com.timekeeper.bibexpo.participant.model.enums.ExportField;
import com.timekeeper.bibexpo.participant.model.enums.SearchType;
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
                    **Roles:** ROOT, ADMIN, ORGANIZER_ADMIN, ORGANIZER_USER. \
                    Create a single participant for an event. \
                    Adding is allowed while the event is in draft or published; it is blocked once the event is completed or cancelled. \

                    **Required:** bibNumber, fullName, raceId, categoryId, gender. \

                    **Conditionally required:** at least one of phoneNumber / email, and at least one of dateOfBirth / age. \

                    **Optional:** chipNumber, country, city, bibCollectedAt, goodies, additionalFields, emergencyContactName, emergencyContactPhone, notes. \

                    **additionalFields:** free-form key-value columns, matching the dynamic columns retained from a CSV import: \
                    at most 50, names up to 100 characters, values up to 500. \

                    **goodies:** every goody name must already be on the event's goody list; it is matched ignoring case and \
                    surrounding spaces and stored under the list's spelling. An unknown name returns 400, and values are up to 100 characters. \

                    **Uniqueness:** bibNumber must be unique within the event; chipNumber, when provided, must also be unique within the event (blank or omitted chip numbers are exempt). \

                    **Normalization:** fullName is stored and returned in UPPERCASE; email in lowercase."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Participant created successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ParticipantResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request data, or the event is completed or cancelled",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden - user not authorized for this event",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Event, race, or category not found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "BIB number or chip number already assigned to another participant in this event",
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
                    **Roles:** ROOT, ADMIN, ORGANIZER_ADMIN, ORGANIZER_USER. \
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
            description = "**Roles:** ROOT, ADMIN, ORGANIZER_ADMIN, ORGANIZER_USER. Returns the total number of "
                    + "participants registered for an event as a simple count object {count: number}"
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

    @GetMapping("/{eventId}/participants/lookup")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER', 'ROLE_DISTRIBUTOR')")
    @Operation(
            summary = "Lookup participants using LSI (cost-efficient alternative to search)",
            description = """
                    **Roles:** ROOT, ADMIN, ORGANIZER_ADMIN, ORGANIZER_USER, DISTRIBUTOR. \
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
                    **Roles:** ROOT, ADMIN, ORGANIZER_ADMIN, ORGANIZER_USER. \
                    Downloads every participant of the event as a UTF-8, comma-separated CSV file named \
                    `participants-event-{eventId}.csv`. The first row holds the headers, then one row per \
                    participant, sorted by bib number as text (so `10` comes before `2`).

                    **Choosing columns (`fields`):** \
                    Send the field names you want; the columns come out in the order you send them. \
                    Both `?fields=FULL_NAME&fields=BIB_NUMBER` and `?fields=FULL_NAME,BIB_NUMBER` work. \
                    A field sent twice is exported once. Send no `fields` to export every field in the \
                    default order below. An unknown field name returns 400.

                    **Default order, with the header each field writes:**
                    1. `CHIP_NUMBER` → Chip Number
                    2. `BIB_NUMBER` → Bib Number
                    3. `FULL_NAME` → Full Name
                    4. `PHONE_NUMBER` → Phone Number
                    5. `EMAIL` → Email
                    6. `RACE_NAME` → Race Name
                    7. `CATEGORY_NAME` → Category Name
                    8. `AGE` → Age
                    9. `GENDER` → Gender
                    10. `DATE_OF_BIRTH` → Date Of Birth
                    11. `CITY` → City
                    12. `COUNTRY` → Country
                    13. `GOODIES` → one column per goody (see below)
                    14. `BIB_COLLECTED_AT` → Bib Collected At
                    15. `EMERGENCY_CONTACT_NAME` → Emergency Contact Name
                    16. `EMERGENCY_CONTACT_PHONE` → Emergency Contact Phone
                    17. `NOTES` → Notes
                    18. `ADDITIONAL_FIELDS` → one column per additional field (see below)

                    **GOODIES and ADDITIONAL_FIELDS** are groups, not single columns. `GOODIES` adds one \
                    column per goody name any participant carries, holding what that participant is owed \
                    (for example a T-shirt size), not what was handed out. `ADDITIONAL_FIELDS` adds one \
                    column per extra column kept from the import (the participant's `additionalFields` keys). \
                    Inside a group the columns are sorted by name. A participant without a value gets an \
                    empty cell, and an event with no goodies or no additional fields gets no columns from \
                    that group.

                    **Header names:** a goody or additional-field key is written as words: split at \
                    camelCase and underscores, each word capitalised. `tShirtSize` → "T Shirt Size", \
                    `blood_group` → "Blood Group", `Medal` → "Medal". \
                    If a header is already used by another column (ignoring case), the fixed field keeps \
                    it and the group column gets its group added: an additional field `city` becomes \
                    "City (Additional)", a goody named `Notes` becomes "Notes (Goody)". Goody headers are \
                    settled before additional-field headers.

                    **Cell values:** race and category are exported by their current names. Any cell or \
                    header starting with `=`, `+`, `-`, `@`, a tab or a carriage return is prefixed with \
                    `'` so spreadsheet apps show it as text instead of running it as a formula."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "CSV file, sent as an attachment",
                    content = @Content(mediaType = "text/csv")),
            @ApiResponse(responseCode = "400", description = "An unknown field name in `fields`",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden - insufficient permissions",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Event not found, or not visible to the caller",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<byte[]> exportParticipants(
            @Parameter(description = "Event ID", required = true, example = "1")
            @PathVariable Long eventId,

            @Parameter(description = "Fields to export, in the column order wanted. Omit to export every "
                    + "field in the default order.",
                    array = @ArraySchema(schema = @Schema(implementation = ExportField.class)))
            @RequestParam(required = false) List<ExportField> fields,

            @AuthenticationPrincipal User currentUser
    );

    @DeleteMapping("/{eventId}/participants")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @Operation(
            summary = "Delete all participants for an event",
            description = """
                    **Roles:** ROOT, ADMIN, ORGANIZER_ADMIN, ORGANIZER_USER. \
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
                    **Roles:** ROOT, ADMIN, ORGANIZER_ADMIN, ORGANIZER_USER. \
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
                    **Roles:** ROOT, ADMIN, ORGANIZER_ADMIN, ORGANIZER_USER. \
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
                    **Roles:** ROOT, ADMIN, ORGANIZER_ADMIN, ORGANIZER_USER. \
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
                    - BIB Number: via the separate `newBibNumber` field; because bibNumber is the DynamoDB sort key the record is deleted and recreated under the new number. \
                    Refused (400) once the bib has been handed out, because the hand-out history stays with the old number; undo the collection first. \

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
            @ApiResponse(responseCode = "400", description = "Invalid request data - invalid race/category ID, invalid field values, field validation errors, the event is completed or cancelled, or a bib number change on a bib already handed out",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access denied - user not authorized for this event",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Event, participant, race, or category not found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "BIB number or chip number already assigned to another participant in this event",
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
                    **Roles:** ROOT, ADMIN, ORGANIZER_ADMIN, ORGANIZER_USER. \
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
