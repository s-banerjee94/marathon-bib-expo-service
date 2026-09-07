package com.timekeeper.bibexpo.inventory.exception;

import com.timekeeper.bibexpo.shared.error.ApiException;
import org.springframework.http.HttpStatus;

public class InsufficientStockException extends ApiException {

    public static final String DEFAULT_MESSAGE = "There is not enough stock at this location to complete this action.";

    public InsufficientStockException() {
        super(HttpStatus.CONFLICT, DEFAULT_MESSAGE);
    }
}
