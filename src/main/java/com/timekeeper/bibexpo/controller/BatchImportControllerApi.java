package com.timekeeper.bibexpo.controller;

import com.timekeeper.bibexpo.exception.ErrorResponse;
import com.timekeeper.bibexpo.model.dto.response.BatchImportResponse;
import com.timekeeper.bibexpo.model.dto.response.BatchJobStatusResponse;
import com.timekeeper.bibexpo.model.dto.response.ImportErrorListResponse;
import com.timekeeper.bibexpo.model.entity.User;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Batch Import", description = "Async CSV import via Spring Batch — returns 202 immediately")
@SecurityRequirement(name = "bearerAuth")
public interface BatchImportControllerApi {

    @PostMapping(value = "/{eventId}/participants/batch-import", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN')")
    @Operation(
            summary = "Launch async CSV import (202 Accepted)",
            description = """
                    Deletes all existing participants for the event synchronously, then launches a Spring Batch job \
                    asynchronously. Returns 202 immediately with a jobExecutionId. \
                    Poll GET .../batch-import/{jobExecutionId}/status to track progress. \
                    Returns 409 if a batch import is already running for the same event."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Import job accepted and started",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = BatchImportResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid file or request",
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
            @AuthenticationPrincipal User currentUser
    );

    @GetMapping("/{eventId}/participants/batch-import/{jobExecutionId}/status")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN')")
    @Operation(
            summary = "Poll batch import job status",
            description = "Reads Spring Batch metadata to return current status and step counters. " +
                    "Status values: STARTING, STARTED, COMPLETED, FAILED, STOPPED, ABANDONED, UNKNOWN."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Status retrieved",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = BatchJobStatusResponse.class))),
            @ApiResponse(responseCode = "400", description = "Job execution not found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access forbidden",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<BatchJobStatusResponse> getBatchImportStatus(
            @Parameter(description = "Event ID", required = true) @PathVariable Long eventId,
            @Parameter(description = "Job execution ID returned by POST batch-import", required = true)
            @PathVariable Long jobExecutionId,
            @AuthenticationPrincipal User currentUser
    );

    @GetMapping("/{eventId}/participants/batch-import/latest/errors")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN')")
    @Operation(
            summary = "Get paginated errors from the latest batch import",
            description = "Returns row-level validation errors from the most recent batch import for the event. " +
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
}
