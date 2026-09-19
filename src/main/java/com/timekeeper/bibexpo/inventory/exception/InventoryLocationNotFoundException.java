package com.timekeeper.bibexpo.inventory.exception;

import com.timekeeper.bibexpo.shared.error.ApiException;
import org.springframework.http.HttpStatus;

public class InventoryLocationNotFoundException extends ApiException {

    public static final String DEFAULT_MESSAGE = "The location you requested does not exist.";

    public InventoryLocationNotFoundException() {
        super(HttpStatus.NOT_FOUND, DEFAULT_MESSAGE);
    }
}
