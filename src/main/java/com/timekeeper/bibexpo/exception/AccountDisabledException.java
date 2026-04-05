package com.timekeeper.bibexpo.exception;

public class AccountDisabledException extends RuntimeException {

    public AccountDisabledException(String message) {
        super(message);
    }
}
