package com.timekeeper.bibexpo.billing.exception;

import com.timekeeper.bibexpo.shared.error.ApiException;
import org.springframework.http.HttpStatus;

public class BillNotFoundException extends ApiException {
    public BillNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }
}
