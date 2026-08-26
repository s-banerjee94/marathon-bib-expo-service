package com.timekeeper.bibexpo.importer.exception;

public class ImportAlreadyRunningException extends RuntimeException {
    public ImportAlreadyRunningException(String message) {
        super(message);
    }
}
