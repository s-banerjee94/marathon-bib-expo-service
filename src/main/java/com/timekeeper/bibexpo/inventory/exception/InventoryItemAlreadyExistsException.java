package com.timekeeper.bibexpo.inventory.exception;

import com.timekeeper.bibexpo.shared.error.ApiException;
import org.springframework.http.HttpStatus;

public class InventoryItemAlreadyExistsException extends ApiException {

    public static final String DEFAULT_MESSAGE = "An item with this name already exists.";

    public InventoryItemAlreadyExistsException() {
        super(HttpStatus.CONFLICT, DEFAULT_MESSAGE);
    }
}
