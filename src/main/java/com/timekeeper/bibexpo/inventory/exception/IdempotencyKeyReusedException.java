package com.timekeeper.bibexpo.inventory.exception;

import com.timekeeper.bibexpo.shared.error.ApiException;
import org.springframework.http.HttpStatus;

public class IdempotencyKeyReusedException extends ApiException {

    public static final String DEFAULT_MESSAGE =
            "You already sent a different change with this Idempotency-Key, so send this one with a new key.";

    public IdempotencyKeyReusedException() {
        super(HttpStatus.UNPROCESSABLE_ENTITY, DEFAULT_MESSAGE);
    }
}
