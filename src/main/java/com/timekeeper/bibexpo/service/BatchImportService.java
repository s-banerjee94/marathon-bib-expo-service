package com.timekeeper.bibexpo.service;

import com.timekeeper.bibexpo.model.dto.response.BatchImportResponse;
import com.timekeeper.bibexpo.model.dto.response.BatchJobStatusResponse;
import com.timekeeper.bibexpo.model.dto.response.ImportErrorListResponse;
import com.timekeeper.bibexpo.model.dto.response.ImportFieldResponse;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.enums.ImportMode;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface BatchImportService {

    /**
     * Validate the column mapping, save the file and mapping to temp disk, then launch the async
     * Spring Batch job.
     *
     * <p>In {@link ImportMode#IMPORT} mode the job wipes existing participants before loading and is
     * permitted only while the event is in draft. In {@link ImportMode#ADD_ON} mode the job appends
     * without wiping and is permitted while the event is draft or published.
     *
     * @param eventId     event to import into
     * @param file        uploaded CSV file
     * @param mappingJson frontend-supplied column mapping as JSON (see {@code ImportMappingRequest})
     * @param mode        whether this run is a full import or an add-on
     * @param currentUser user launching the import
     * @return 202-style response with jobExecutionId and initial STARTED status
     */
    BatchImportResponse launchImport(Long eventId, MultipartFile file, String mappingJson, ImportMode mode, User currentUser);

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
     * Cooperatively stop a running batch import. Signals Spring Batch via JobOperator.stop()
     * so the next chunk boundary halts execution. The afterJob listener then marks the
     * ImportJob row FAILED with a "Stopped by user" reason. Returns 409 if the job is
     * not currently IN_PROGRESS for the given event.
     * @param eventId        event the job was launched for
     * @param jobExecutionId the ID returned by launchImport
     * @param currentUser    user invoking the stop
     * @return the final status snapshot after signalling stop
     */
    BatchJobStatusResponse stopImport(Long eventId, Long jobExecutionId, User currentUser);

    /**
     * Retrieve paginated errors from the most recent batch import for an event.
     * Resolves the latest job from event_latest_import, then queries DynamoDB for errors.
     * Returns an empty response if no batch import has been run for the event.
     * @param eventId          event the job was launched for
     * @param limit            max errors per page (default 50)
     * @param lastEvaluatedKey base64-encoded pagination token from a previous response, or null for first page
     * @return paginated list of validation errors with a pagination token for the next page
     */
    ImportErrorListResponse getLatestBatchImportErrors(Long eventId, int limit, String lastEvaluatedKey);

    /**
     * Returns the canonical catalog of participant fields a CSV column can be mapped to.
     * Used by the frontend to populate target-field choices and by the backend to validate mappings.
     * @return the list of mappable fields with key, label, and required flag
     */
    List<ImportFieldResponse> getImportFields();
}
