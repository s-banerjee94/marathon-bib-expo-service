package com.timekeeper.bibexpo.participant.exception;

import com.timekeeper.bibexpo.exception.CategoryNotFoundException;
import com.timekeeper.bibexpo.shared.error.ApiException;
import org.springframework.http.HttpStatus;

/**
 * Raised when a participant's race and category do not belong together — typically a request that
 * moves the participant to a different race while leaving the category pointing at the old one.
 * Distinct from {@link CategoryNotFoundException} so the failure is reported as a bad request rather
 * than a missing category the caller never asked for.
 */
public class RaceCategoryMismatchException extends ApiException {

    public static final String DEFAULT_MESSAGE =
            "Choose a category that belongs to the selected race.";

    public RaceCategoryMismatchException() {
        super(HttpStatus.BAD_REQUEST, DEFAULT_MESSAGE);
    }
}
