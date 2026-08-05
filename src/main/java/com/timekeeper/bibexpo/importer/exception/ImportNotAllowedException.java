package com.timekeeper.bibexpo.importer.exception;

public class ImportNotAllowedException extends RuntimeException {
    public ImportNotAllowedException(String message) {
        super(message);
    }
}
