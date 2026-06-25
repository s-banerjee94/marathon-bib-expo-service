package com.timekeeper.bibexpo.exception;

public class OrganizationDeletionNotAllowedException extends RuntimeException {
    public OrganizationDeletionNotAllowedException(String message) {
        super(message);
    }
}
