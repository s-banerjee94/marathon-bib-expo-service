package com.timekeeper.bibexpo.billing.exception;

import com.timekeeper.bibexpo.exception.ApiException;
import org.springframework.http.HttpStatus;

public class BillNotFoundException extends ApiException {
    public BillNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }
}
