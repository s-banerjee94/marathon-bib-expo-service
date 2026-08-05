package com.timekeeper.bibexpo.importer.exception;

import com.timekeeper.bibexpo.shared.error.ApiException;
import org.springframework.http.HttpStatus;

/**
 * Raised when an import job cannot be reached: it does not exist, or it belongs to another event.
 * The two cases share one message on purpose, so the endpoint cannot be used to probe which job
 * ids exist outside the caller's own events.
 */
public class ImportJobNotFoundException extends ApiException {

    public ImportJobNotFoundException() {
        super(HttpStatus.NOT_FOUND, "Not Found", "Import job not found.");
    }
}
