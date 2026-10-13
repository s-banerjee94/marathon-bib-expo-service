package com.timekeeper.bibexpo.inventory.exception;

import com.timekeeper.bibexpo.shared.error.ApiException;
import org.springframework.http.HttpStatus;

public class StockPostingNotFoundException extends ApiException {

    public static final String DEFAULT_MESSAGE = "The stock change you requested does not exist.";

    public StockPostingNotFoundException() {
        super(HttpStatus.NOT_FOUND, DEFAULT_MESSAGE);
    }
}
