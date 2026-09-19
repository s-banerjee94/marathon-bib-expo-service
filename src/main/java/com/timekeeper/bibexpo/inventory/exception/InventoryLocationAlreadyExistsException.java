package com.timekeeper.bibexpo.inventory.exception;

import com.timekeeper.bibexpo.shared.error.ApiException;
import org.springframework.http.HttpStatus;

public class InventoryLocationAlreadyExistsException extends ApiException {

    public static final String DEFAULT_MESSAGE = "A location with this name already exists in this scope.";

    public InventoryLocationAlreadyExistsException() {
        super(HttpStatus.CONFLICT, DEFAULT_MESSAGE);
    }
}
