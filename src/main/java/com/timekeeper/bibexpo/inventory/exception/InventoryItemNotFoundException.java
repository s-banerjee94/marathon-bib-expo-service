package com.timekeeper.bibexpo.inventory.exception;

import com.timekeeper.bibexpo.shared.error.ApiException;
import org.springframework.http.HttpStatus;

public class InventoryItemNotFoundException extends ApiException {

    public static final String DEFAULT_MESSAGE = "The item you requested does not exist.";

    public InventoryItemNotFoundException() {
        super(HttpStatus.NOT_FOUND, DEFAULT_MESSAGE);
    }
}
