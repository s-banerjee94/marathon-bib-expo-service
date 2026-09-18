package com.timekeeper.bibexpo.importer.controller;

import com.timekeeper.bibexpo.importer.model.dto.response.BatchImportResponse;
import com.timekeeper.bibexpo.importer.model.dto.response.BatchJobStatusResponse;
import com.timekeeper.bibexpo.importer.model.dto.response.ImportErrorListResponse;
import com.timekeeper.bibexpo.importer.model.dto.response.ImportFieldResponse;
import com.timekeeper.bibexpo.importer.model.enums.ImportMode;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "Batch Import", description = "Async CSV import via Spring Batch — returns 202 immediately")
@SecurityRequirement(name = "bearerAuth")
public interface BatchImportControllerApi {

    @PostMapping(value = "/{eventId}/participants/batch-import", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @Operation(
            summary = "Launch async CSV import with dynamic column mapping (202 Accepted)",
            description = """
                    **Roles:** `ROOT`, `ADMIN`, `ORGANIZER_ADMIN`, `ORGANIZER_USER`

                    Accepts the CSV file plus a JSON column mapping (see ImportMappingRequest) in the same \
                    multipart request. Columns are matched by zero-based physical position, so the file header \
                    is not validated server-side. The mapping is validated up front (unknown fields, missing \
                    required fields, or duplicate mappings return 400). \

                    The `mode` parameter selects the run type: \
                    - IMPORT (default): a full load. Existing participants are wiped when the job starts, then the \
                    file is loaded. Allowed only while the event is in DRAFT. \
                    - ADD_ON: appends walk-ins without wiping. Allowed while the event is DRAFT or PUBLISHED; \
                    blocked once COMPLETED or CANCELLED. It only adds: a row whose bib is already registered for \
                    the event is skipped and reported as a DUPLICATE_BIB row error, so that participant's record, \
                    including a collected bib and goodies already handed out, stays exactly as it was. \

                    A column marked as goodies may hold at most 50 different values, counted spelling for \
                    spelling as they are stored, and for an add-on together with the values the event already \
                    carries. A column holding more is a column marked as goodies by mistake, so the import is \
                    refused with 400 before anything is written and the offending columns are named. \

                    A Spring Batch job runs asynchronously and returns 202 immediately with a jobExecutionId. \
                    Poll GET .../batch-import/{jobExecutionId}/status to track progress. Returns 409 if a batch \
                    import is already running for the same event. A successful launch is recorded in the audit \
                    log (entity PARTICIPANT, action IMPORT)."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Import job accepted and started",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = BatchImportResponse.class))),
            @ApiResponse(responseCode = "400",
                    description = "Invalid file or request, or a column marked as goodies holds more than 50 "
                            + "different values",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Event not found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "A batch import is already running for this event",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<BatchImportResponse> launchBatchImport(
            @Parameter(description = "Event ID", required = true) @PathVariable Long eventId,
            @Parameter(description = "CSV file", required = true) @RequestParam("file") MultipartFile file,
            @Parameter(description = "Column mapping JSON (ImportMappingRequest)", required = true)
            @RequestPart("mapping") String mapping,
            @Parameter(description = "Run mode: IMPORT (full load, draft only, wipes existing) or ADD_ON "
                    + "(append walk-ins, draft or published; bibs already registered are skipped). Defaults to IMPORT.")
            @RequestParam(value = "mode", required = false, defaultValue = "IMPORT") ImportMode mode,
            @AuthenticationPrincipal User currentUser
    );

    @GetMapping("/{eventId}/participants/batch-import/{jobExecutionId}/status")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @Operation(
            summary = "Poll batch import job status",
            description = "Reads Spring Batch metadata to return current status and step counters. " +
                    "Status values: STARTING, STARTED, COMPLETED, FAILED, STOPPED, ABANDONED, UNKNOWN."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Status retrieved",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = BatchJobStatusResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Import job not found, or it belongs to another event",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<BatchJobStatusResponse> getBatchImportStatus(
            @Parameter(description = "Event ID", required = true) @PathVariable Long eventId,
            @Parameter(description = "Job execution ID returned by POST batch-import", required = true)
            @PathVariable Long jobExecutionId,
            @AuthenticationPrincipal User currentUser
    );

    @PostMapping("/{eventId}/participants/batch-import/{jobExecutionId}/stop")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @Operation(
            summary = "Cooperatively stop a running batch import",
            description = """
                    Signals Spring Batch to terminate the job at the next chunk boundary. \
                    The job will transition to STOPPING then STOPPED, and the ImportJob row \
                    will be marked FAILED with a 'Stopped by user' reason once the worker thread exits. \
                    Returns 409 if the job is not currently running."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Stop signal sent",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = BatchJobStatusResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Event not found, or no such import job for it",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Import is not currently running",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<BatchJobStatusResponse> stopBatchImport(
            @Parameter(description = "Event ID", required = true) @PathVariable Long eventId,
            @Parameter(description = "Job execution ID returned by POST batch-import", required = true)
            @PathVariable Long jobExecutionId,
            @AuthenticationPrincipal User currentUser
    );

    @GetMapping("/{eventId}/participants/batch-import/latest/errors")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @Operation(
            summary = "Get paginated errors from the latest batch import",
            description = "**Roles:** `ROOT`, `ADMIN`, `ORGANIZER_ADMIN`, `ORGANIZER_USER`\n\n" +
                    "Returns row-level errors from the most recent batch import for the event, including the " +
                    "DUPLICATE_BIB rows an ADD_ON run skipped because the bib was already registered. " +
                    "Use lastEvaluatedKey from the response to retrieve subsequent pages. " +
                    "Returns an empty list if no import has been run."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Errors retrieved",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ImportErrorListResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Event not found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<ImportErrorListResponse> getLatestBatchImportErrors(
            @Parameter(description = "Event ID", required = true) @PathVariable Long eventId,
            @Parameter(description = "Max errors per page (default 50)") @RequestParam(defaultValue = "50") int limit,
            @Parameter(description = "Pagination token from previous response") @RequestParam(required = false) String lastEvaluatedKey,
            @AuthenticationPrincipal User currentUser
    );

    @GetMapping("/{eventId}/participants/import-fields")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @Operation(
            summary = "List the participant fields a CSV column can be mapped to",
            description = "Returns the canonical catalog of target fields (key, label, required flag) the " +
                    "frontend should offer when building a column mapping. Use the returned keys as targetField " +
                    "values in the import mapping so the two sides never drift."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Field catalog retrieved",
                    content = @Content(mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = ImportFieldResponse.class)))),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Event not found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<List<ImportFieldResponse>> getImportFields(
            @Parameter(description = "Event ID", required = true) @PathVariable Long eventId,
            @AuthenticationPrincipal User currentUser
    );
}
