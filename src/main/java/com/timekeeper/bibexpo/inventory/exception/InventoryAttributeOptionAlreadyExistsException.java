package com.timekeeper.bibexpo.inventory.exception;

import com.timekeeper.bibexpo.shared.error.ApiException;
import org.springframework.http.HttpStatus;

public class InventoryAttributeOptionAlreadyExistsException extends ApiException {

    public static final String DEFAULT_MESSAGE = "A value with this name already exists for this attribute.";

    public InventoryAttributeOptionAlreadyExistsException() {
        super(HttpStatus.CONFLICT, DEFAULT_MESSAGE);
    }
}
