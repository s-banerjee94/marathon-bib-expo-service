package com.timekeeper.bibexpo.organization.exception;

public class OrganizationDeletionNotAllowedException extends RuntimeException {
    public OrganizationDeletionNotAllowedException(String message) {
        super(message);
    }
}
