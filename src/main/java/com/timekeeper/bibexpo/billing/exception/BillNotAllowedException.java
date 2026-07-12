package com.timekeeper.bibexpo.billing.exception;

import com.timekeeper.bibexpo.exception.ApiException;
import org.springframework.http.HttpStatus;

public class BillNotAllowedException extends ApiException {
    public BillNotAllowedException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
