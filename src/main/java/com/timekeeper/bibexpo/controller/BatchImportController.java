package com.timekeeper.bibexpo.controller;

import com.timekeeper.bibexpo.model.dto.response.BatchImportResponse;
import com.timekeeper.bibexpo.model.dto.response.BatchJobStatusResponse;
import com.timekeeper.bibexpo.model.dto.response.ImportErrorListResponse;
import com.timekeeper.bibexpo.model.dto.response.ImportFieldResponse;
import com.timekeeper.bibexpo.exception.ErrorResponse;
import com.timekeeper.bibexpo.exception.ImportNotAllowedException;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.enums.ImportMode;
import com.timekeeper.bibexpo.service.BatchImportService;
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

        log.info("Batch import request - event: {}, file: {}, mode: {}, user: {}",
                eventId, file.getOriginalFilename(), mode, currentUser.getUsername());

        BatchImportResponse response = batchImportService.launchImport(eventId, file, mapping, mode, currentUser);

        log.info("Batch import job {} launched for event {} (mode {})",
                response.getJobExecutionId(), eventId, mode);

        return ResponseEntity.accepted().body(response);
    }

    @Override
    public ResponseEntity<BatchJobStatusResponse> getBatchImportStatus(
            Long eventId, Long jobExecutionId, User currentUser) {

        log.info("Status check - jobExecutionId: {}, event: {}, user: {}",
                jobExecutionId, eventId, currentUser.getUsername());

        return ResponseEntity.ok(batchImportService.getJobStatus(eventId, jobExecutionId));
    }

    @Override
    public ResponseEntity<BatchJobStatusResponse> stopBatchImport(
            Long eventId, Long jobExecutionId, User currentUser) {

        log.info("Stop request - jobExecutionId: {}, event: {}, user: {}",
                jobExecutionId, eventId, currentUser.getUsername());

        return ResponseEntity.ok(batchImportService.stopImport(eventId, jobExecutionId, currentUser));
    }

    @Override
    public ResponseEntity<ImportErrorListResponse> getLatestBatchImportErrors(
            Long eventId, int limit, String lastEvaluatedKey, User currentUser) {

        log.info("Latest errors request - event: {}, limit: {}, user: {}", eventId, limit, currentUser.getUsername());

        return ResponseEntity.ok(batchImportService.getLatestBatchImportErrors(eventId, limit, lastEvaluatedKey));
    }

    @Override
    public ResponseEntity<List<ImportFieldResponse>> getImportFields(Long eventId, User currentUser) {
        return ResponseEntity.ok(batchImportService.getImportFields());
    }

    @ExceptionHandler(ImportNotAllowedException.class)
    public ResponseEntity<ErrorResponse> handleImportNotAllowed(
            ImportNotAllowedException ex, WebRequest request) {
        log.info("Import not allowed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(), request));
    }
}
