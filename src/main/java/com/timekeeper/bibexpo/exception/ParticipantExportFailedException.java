package com.timekeeper.bibexpo.exception;

import com.timekeeper.bibexpo.shared.error.ApiException;
import org.springframework.http.HttpStatus;

public class ParticipantExportFailedException extends ApiException {

    public ParticipantExportFailedException(Throwable cause) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, "Export Failed",
                "The export could not be generated.", cause);
    }
}
