package com.timekeeper.bibexpo.batch;

import com.timekeeper.bibexpo.validator.ValidationError;

import java.util.List;

public class BatchValidationException extends RuntimeException {

    public static final String TYPE_VALIDATION = "VALIDATION_ERROR";
    public static final String TYPE_PROCESSING = "PROCESSING_ERROR";

    private final List<ValidationError> validationErrors;
    private final String errorType;

    public BatchValidationException(String message, List<ValidationError> validationErrors) {
        this(message, validationErrors, TYPE_VALIDATION);
    }

    public BatchValidationException(String message, List<ValidationError> validationErrors, String errorType) {
        super(message);
        this.validationErrors = validationErrors;
        this.errorType = errorType;
    }

    public List<ValidationError> getValidationErrors() {
        return validationErrors;
    }

    public String getErrorType() {
        return errorType;
    }
}
