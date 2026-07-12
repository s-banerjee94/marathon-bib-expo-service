package com.timekeeper.bibexpo.exception;

import org.springframework.http.HttpStatus;

public class CsvImportException extends ApiException {

    public CsvImportException(String message) {
        super(HttpStatus.BAD_REQUEST, "CSV Import Failed", message);
    }

    public CsvImportException(String message, Throwable cause) {
        super(HttpStatus.BAD_REQUEST, "CSV Import Failed", message, cause);
    }
}
