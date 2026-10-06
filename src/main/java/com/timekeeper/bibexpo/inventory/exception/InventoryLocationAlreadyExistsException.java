package com.timekeeper.bibexpo.inventory.exception;

import com.timekeeper.bibexpo.shared.error.ApiException;
import org.springframework.http.HttpStatus;

public class InventoryLocationAlreadyExistsException extends ApiException {

    public static final String DEFAULT_MESSAGE = "Your organization already has a location with this name.";

    public InventoryLocationAlreadyExistsException() {
        super(HttpStatus.CONFLICT, DEFAULT_MESSAGE);
    }
}
