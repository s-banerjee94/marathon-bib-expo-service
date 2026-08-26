package com.timekeeper.bibexpo.importer.exception;

public class InvalidCsvFormatException extends RuntimeException {

    public InvalidCsvFormatException(String message) {
        super(message);
    }

    public InvalidCsvFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
