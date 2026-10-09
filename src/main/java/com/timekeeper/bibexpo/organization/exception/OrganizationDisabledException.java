package com.timekeeper.bibexpo.organization.exception;

import com.timekeeper.bibexpo.shared.error.ApiException;
import org.springframework.http.HttpStatus;

public class OrganizationDisabledException extends ApiException {
    public OrganizationDisabledException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
