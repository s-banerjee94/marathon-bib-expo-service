package com.timekeeper.bibexpo.batch;

import com.timekeeper.bibexpo.validator.ValidationError;

import java.util.List;

public class BatchValidationException extends RuntimeException {

    private final List<ValidationError> validationErrors;

    public BatchValidationException(String message, List<ValidationError> validationErrors) {
        super(message);
        this.validationErrors = validationErrors;
    }

    public List<ValidationError> getValidationErrors() {
        return validationErrors;
    }
}
