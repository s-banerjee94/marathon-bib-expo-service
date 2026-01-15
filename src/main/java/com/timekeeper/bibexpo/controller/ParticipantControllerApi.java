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
import org.springframework.web.multipart.MultipartFile;

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
                    Required fields: chipNumber, bibNumber, fullName, raceId, raceName, categoryId, categoryName, gender. \
                    Conditionally required: Either phoneNumber or email must be provided. Either dateOfBirth or age must be provided."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Participant created successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ParticipantResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request data",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Event, race, or category not found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Participant with BIB number already exists",
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

    @PostMapping(value = "/{eventId}/participants/import", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN')")
    @Operation(
            summary = "Import participants from CSV file (FULL REPLACE)",
            description = """
                    ⚠️ IMPORTANT: This is a FULL REPLACE import. ALL existing participants for this event will be DELETED before importing the CSV. \
                    Upload a CSV file to bulk import participants for an event. \
                    CSV must contain standard columns: CHIP No., BIB No., NAME, DOB(dd-mm-yyy), Age, Gender, Race, Category, Phone, Email-Id, Country, City. \
                    Additional columns after 'City' are treated as dynamic goodies (e.g., T-Shirt Size, Cap Size). \
                    The system will detect goodies columns automatically and store them in the event metadata. \
                    Returns a lightweight response with importId. Use GET /api/events/{eventId}/imports/{importId} to retrieve detailed results, error summary, and error list. \
                    Only ROOT, ADMIN, and ORGANIZER_ADMIN roles can import participants."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Import job created and processing completed. Use importId to query details.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ImportParticipantsResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid CSV format or data",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Event not found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "413", description = "File too large",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<ImportParticipantsResponse> importParticipants(
            @Parameter(description = "Event ID", required = true)
            @PathVariable Long eventId,

            @Parameter(description = "CSV file containing participant data", required = true)
            @RequestParam("file") MultipartFile file,

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

    @GetMapping("/{eventId}/participants/{bibNumber}")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @Operation(
            summary = "Get participant by bib number",
            description = "Retrieve a specific participant by their bib number for a given event"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Participant found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ParticipantResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden"),
            @ApiResponse(responseCode = "404", description = "Participant or event not found")
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
            summary = "Update participant",
            description = "Update participant details. Only non-null fields will be updated. BIB number can be changed (requires delete+create operation). Goodies cannot be updated via this endpoint."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Participant updated successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ParticipantResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request data",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access denied - not authorized for this event",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Event or participant not found",
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

    @GetMapping("/{eventId}/participants/count")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @Operation(
            summary = "Get participant count for an event",
            description = "Returns the total number of participants registered for an event"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Count retrieved successfully"),
            @ApiResponse(responseCode = "403", description = "Access forbidden"),
            @ApiResponse(responseCode = "404", description = "Event not found")
    })
    ResponseEntity<Map<String, Long>> getParticipantCount(
            @Parameter(description = "Event ID", required = true)
            @PathVariable Long eventId,

            @AuthenticationPrincipal User currentUser
    );

    @GetMapping("/{eventId}/imports")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN')")
    @Operation(
            summary = "Get import job history for an event",
            description = "Retrieve paginated list of participant import jobs for a specific event with their status and error summaries"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Import jobs retrieved successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ImportJobListResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden"),
            @ApiResponse(responseCode = "404", description = "Event not found")
    })
    ResponseEntity<ImportJobListResponse> getImportJobs(
            @Parameter(description = "Event ID", required = true)
            @PathVariable Long eventId,

            @Parameter(description = "Page number (0-indexed, default: 0)")
            @RequestParam(defaultValue = "0") Integer page,

            @Parameter(description = "Page size (default: 20)")
            @RequestParam(defaultValue = "20") Integer size,

            @AuthenticationPrincipal User currentUser
    );

    @GetMapping("/{eventId}/imports/{importId}")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN')")
    @Operation(
            summary = "Get import job details",
            description = "Retrieve detailed information about a specific import job including error summary"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Import job details retrieved successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ImportJobResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden"),
            @ApiResponse(responseCode = "404", description = "Import job or event not found")
    })
    ResponseEntity<ImportJobResponse> getImportJobDetails(
            @Parameter(description = "Event ID", required = true)
            @PathVariable Long eventId,

            @Parameter(description = "Import job ID", required = true)
            @PathVariable String importId,

            @AuthenticationPrincipal User currentUser
    );

    @GetMapping("/{eventId}/imports/{importId}/errors")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN')")
    @Operation(
            summary = "Get import errors with pagination",
            description = """
                    Retrieve paginated list of errors from a specific import job. \
                    Errors are stored in DynamoDB with 30-day TTL and automatically deleted after expiration. \
                    Use this endpoint to review all import errors when the response contains more than 100 errors."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Import errors retrieved successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ImportErrorListResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden"),
            @ApiResponse(responseCode = "404", description = "Import job or event not found")
    })
    ResponseEntity<ImportErrorListResponse> getImportErrors(
            @Parameter(description = "Event ID", required = true)
            @PathVariable Long eventId,

            @Parameter(description = "Import job ID", required = true)
            @PathVariable String importId,

            @Parameter(description = "Page number (0-indexed, default: 0)")
            @RequestParam(defaultValue = "0") Integer page,

            @Parameter(description = "Page size (default: 50)")
            @RequestParam(defaultValue = "50") Integer size,

            @AuthenticationPrincipal User currentUser
    );

    @DeleteMapping("/{eventId}/participants")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @Operation(
            summary = "Delete all participants for an event",
            description = """
                    ⚠️ WARNING: This will permanently delete ALL participants for the specified event. \
                    This action cannot be undone. Use with caution."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "All participants deleted successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = DeleteParticipantsResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
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
                    Delete multiple participants by providing their bib numbers. \
                    Maximum 25 participants can be deleted at once. \
                    Returns count of successfully deleted and failed deletions."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Bulk delete completed",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = DeleteParticipantsResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request (empty list or more than 25 items)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
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

    @GetMapping("/{eventId}/participants/search")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @Operation(
            summary = "Search and filter participants",
            description = """
                    Search participants by name, email, phone, or chip number with optional filters. \
                    **Search Logic (OR):** Search term matches ANY of fullName, email, phoneNumber, or chipNumber (case-insensitive, partial match). \
                    **Filter Logic (AND):** All specified filters must match. \
                    Returns paginated results with DynamoDB-style pagination (lastEvaluatedKey). \
                    ⚠️ Note: Uses DynamoDB Scan operation which may be slower for large datasets."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Search completed successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ParticipantListResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid search parameters (e.g., search term too short, invalid age range)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
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

    @DeleteMapping("/{eventId}/participants/{bibNumber}")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @Operation(
            summary = "Delete a participant by bib number",
            description = "Delete a specific participant from an event by their bib number. This action cannot be undone."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Participant deleted successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = DeleteParticipantsResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Event or participant not found",
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

    @GetMapping("/{eventId}/participants/lookup")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @Operation(
            summary = "Lookup participants using LSI (cost-efficient)",
            description = """
                    Lookup participants using DynamoDB Local Secondary Indexes for efficient Query operations. \
                    This is more cost-effective than the search endpoint as it uses Query instead of Scan.

                    **Search Types:**
                    - `NAME`: Search by full name (begins_with, case-insensitive)
                    - `EMAIL`: Search by email (begins_with, case-insensitive)
                    - `PHONE`: Search by phone number (begins_with)
                    - `BIB`: Exact match lookup by bib number
                    - `RACE`: Search by race name (begins_with) - returns all participants in matching races
                    - `CATEGORY`: Search by category name (begins_with) - returns all participants in matching categories

                    **Note:** RACE and CATEGORY searches may return many results and support pagination."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lookup completed successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ParticipantListResponse.class))),
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
    ResponseEntity<ParticipantListResponse> lookupParticipants(
            @Parameter(description = "Event ID", required = true, example = "1")
            @PathVariable Long eventId,

            @Parameter(description = "Type of search: NAME, EMAIL, PHONE, BIB, RACE, CATEGORY", required = true,
                    schema = @Schema(implementation = SearchType.class))
            @RequestParam SearchType searchType,

            @Parameter(description = "Value to search for (uses begins_with for LSI queries)", required = true,
                    example = "JOHN")
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
                    Retrieve aggregated statistics for participants in an event including: \
                    total count, bib collection status, breakdown by race, category, and gender. \
                    ⚠️ This endpoint is coming soon and currently returns placeholder data."""
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
}
