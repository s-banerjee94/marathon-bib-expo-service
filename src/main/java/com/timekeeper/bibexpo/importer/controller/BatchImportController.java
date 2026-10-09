package com.timekeeper.bibexpo.importer.controller;

import com.timekeeper.bibexpo.importer.exception.ImportAlreadyRunningException;
import com.timekeeper.bibexpo.importer.exception.ImportNotRunningException;
import com.timekeeper.bibexpo.importer.exception.InvalidCsvFormatException;
import com.timekeeper.bibexpo.importer.model.dto.response.BatchImportResponse;
import com.timekeeper.bibexpo.importer.model.dto.response.BatchJobStatusResponse;
import com.timekeeper.bibexpo.importer.model.dto.response.ImportErrorListResponse;
import com.timekeeper.bibexpo.importer.model.dto.response.ImportFieldResponse;
import com.timekeeper.bibexpo.importer.model.enums.ImportMode;
import com.timekeeper.bibexpo.importer.service.BatchImportService;
import com.timekeeper.bibexpo.shared.error.ErrorResponse;
import com.timekeeper.bibexpo.user.model.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
@Slf4j
public class BatchImportController implements BatchImportControllerApi {

    private final BatchImportService batchImportService;

    @Override
    public ResponseEntity<BatchImportResponse> launchBatchImport(
            Long eventId, MultipartFile file, String mapping, ImportMode mode, User currentUser) {
        return ResponseEntity.accepted().body(batchImportService.launchImport(eventId, file, mapping, mode, currentUser));
    }

    @Override
    public ResponseEntity<BatchJobStatusResponse> getBatchImportStatus(
            Long eventId, Long jobExecutionId, User currentUser) {
        return ResponseEntity.ok(batchImportService.getJobStatus(eventId, jobExecutionId, currentUser));
    }

    @Override
    public ResponseEntity<BatchJobStatusResponse> stopBatchImport(
            Long eventId, Long jobExecutionId, User currentUser) {
        return ResponseEntity.ok(batchImportService.stopImport(eventId, jobExecutionId, currentUser));
    }

    @Override
    public ResponseEntity<ImportErrorListResponse> getLatestBatchImportErrors(
            Long eventId, int limit, String lastEvaluatedKey, User currentUser) {
        return ResponseEntity.ok(batchImportService.getLatestBatchImportErrors(eventId, limit, lastEvaluatedKey, currentUser));
    }

    @Override
    public ResponseEntity<List<ImportFieldResponse>> getImportFields(Long eventId, User currentUser) {
        return ResponseEntity.ok(batchImportService.getImportFields(eventId, currentUser));
    }

    @ExceptionHandler(ImportAlreadyRunningException.class)
    public ResponseEntity<ErrorResponse> handleImportAlreadyRunning(
            ImportAlreadyRunningException ex, WebRequest request) {
        logRejected(HttpStatus.CONFLICT, ex);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), request));
    }

    @ExceptionHandler(ImportNotRunningException.class)
    public ResponseEntity<ErrorResponse> handleImportNotRunning(
            ImportNotRunningException ex, WebRequest request) {
        logRejected(HttpStatus.CONFLICT, ex);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), request));
    }

    @ExceptionHandler(InvalidCsvFormatException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCsvFormat(
            InvalidCsvFormatException ex, WebRequest request) {
        logRejected(HttpStatus.BAD_REQUEST, ex);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(HttpStatus.BAD_REQUEST, "Invalid CSV Format", ex.getMessage(), request));
    }

    private void logRejected(HttpStatus status, Exception ex) {
        log.atWarn()
                .addKeyValue("event", "request.rejected")
                .addKeyValue("status", status.value())
                .addKeyValue("exception", ex.getClass().getSimpleName())
                .addKeyValue("reason", ex.getMessage())
                .log("Request rejected");
    }
}
