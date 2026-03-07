package com.timekeeper.bibexpo.exception;

public class ImportAlreadyRunningException extends RuntimeException {
    public ImportAlreadyRunningException(String message) {
        super(message);
    }
}
