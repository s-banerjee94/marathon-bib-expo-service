package com.timekeeper.bibexpo.inventory.exception;

import com.timekeeper.bibexpo.shared.error.ApiException;
import org.springframework.http.HttpStatus;

public class InventoryTermAlreadyExistsException extends ApiException {

    public static final String DEFAULT_MESSAGE = "A term with this name already exists.";

    public InventoryTermAlreadyExistsException() {
        super(HttpStatus.CONFLICT, DEFAULT_MESSAGE);
    }
}
