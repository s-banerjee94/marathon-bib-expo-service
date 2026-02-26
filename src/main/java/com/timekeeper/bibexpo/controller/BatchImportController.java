package com.timekeeper.bibexpo.controller;

import com.timekeeper.bibexpo.model.dto.response.BatchImportResponse;
import com.timekeeper.bibexpo.model.dto.response.BatchJobStatusResponse;
import com.timekeeper.bibexpo.model.dto.response.ImportErrorListResponse;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.service.BatchImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
@Slf4j
public class BatchImportController implements BatchImportControllerApi {

    private final BatchImportService batchImportService;

    @Override
    public ResponseEntity<BatchImportResponse> launchBatchImport(
            Long eventId, MultipartFile file, User currentUser) {

        log.info("Batch import request - event: {}, file: {}, user: {}",
                eventId, file.getOriginalFilename(), currentUser.getUsername());

        BatchImportResponse response = batchImportService.launchImport(eventId, file, currentUser);

        log.info("Batch import job {} launched for event {}", response.getJobExecutionId(), eventId);

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
    public ResponseEntity<ImportErrorListResponse> getBatchImportErrors(
            Long eventId, Long jobExecutionId, int limit, String lastEvaluatedKey, User currentUser) {

        log.info("Errors request - jobExecutionId: {}, event: {}, limit: {}, user: {}",
                jobExecutionId, eventId, limit, currentUser.getUsername());

        return ResponseEntity.ok(
                batchImportService.getBatchImportErrors(eventId, jobExecutionId, limit, lastEvaluatedKey));
    }
}
