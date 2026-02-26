package com.timekeeper.bibexpo.service;

import com.timekeeper.bibexpo.model.dto.response.BatchImportResponse;
import com.timekeeper.bibexpo.model.dto.response.BatchJobStatusResponse;
import com.timekeeper.bibexpo.model.dto.response.ImportErrorListResponse;
import com.timekeeper.bibexpo.model.entity.User;
import org.springframework.web.multipart.MultipartFile;

public interface BatchImportService {

    /**
     * Save file to temp disk, delete existing participants, then launch async Spring Batch job.
     * @return 202-style response with jobExecutionId and initial STARTED status
     */
    BatchImportResponse launchImport(Long eventId, MultipartFile file, User currentUser);

    /**
     * Query Spring Batch metadata for job execution status and step counters.
     * Validates that the job's eventId parameter matches the given eventId — prevents
     * one event's users from polling another event's job status.
     * @param eventId        event the job was launched for
     * @param jobExecutionId the ID returned by launchImport
     * @return current status, read/write/skip counts, and start/end times
     */
    BatchJobStatusResponse getJobStatus(Long eventId, Long jobExecutionId);

    /**
     * Retrieve paginated row-level validation errors persisted during a batch import job.
     * Validates that the job belongs to the given eventId before returning results.
     * @param eventId          event the job was launched for
     * @param jobExecutionId   the ID returned by launchImport
     * @param limit            max errors per page (default 50)
     * @param lastEvaluatedKey base64-encoded pagination token from a previous response, or null for first page
     * @return paginated list of validation errors with a pagination token for the next page
     */
    ImportErrorListResponse getBatchImportErrors(Long eventId, Long jobExecutionId,
                                                 int limit, String lastEvaluatedKey);
}
