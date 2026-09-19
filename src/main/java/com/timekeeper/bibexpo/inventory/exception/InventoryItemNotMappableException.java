package com.timekeeper.bibexpo.inventory.exception;

import com.timekeeper.bibexpo.shared.error.ApiException;
import org.springframework.http.HttpStatus;

public class InventoryItemNotMappableException extends ApiException {

    public static final String DEFAULT_MESSAGE = "You can only link a goody to an item that varies by at most one attribute.";

    public InventoryItemNotMappableException() {
        super(HttpStatus.CONFLICT, DEFAULT_MESSAGE);
    }
}
