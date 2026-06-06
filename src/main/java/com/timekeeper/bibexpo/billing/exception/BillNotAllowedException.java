package com.timekeeper.bibexpo.billing.exception;

public class BillNotAllowedException extends RuntimeException {
    public BillNotAllowedException(String message) {
        super(message);
    }
}
