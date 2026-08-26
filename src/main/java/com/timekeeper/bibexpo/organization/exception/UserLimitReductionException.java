package com.timekeeper.bibexpo.organization.exception;

public class UserLimitReductionException extends RuntimeException {
    public UserLimitReductionException(String message) {
        super(message);
    }
}
