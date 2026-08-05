package com.timekeeper.bibexpo.exception;

import com.timekeeper.bibexpo.shared.error.ApiException;
import org.springframework.http.HttpStatus;

public class ParticipantDeletionFailedException extends ApiException {

    public ParticipantDeletionFailedException(Throwable cause) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, "Deletion Failed",
                "The selected participants could not be deleted.", cause);
    }
}
