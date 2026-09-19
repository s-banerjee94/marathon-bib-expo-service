package com.timekeeper.bibexpo.inventory.exception;

import com.timekeeper.bibexpo.shared.error.ApiException;
import org.springframework.http.HttpStatus;

public class InventoryAttributeAlreadyExistsException extends ApiException {

    public static final String DEFAULT_MESSAGE = "An attribute with this name already exists in this scope.";

    public InventoryAttributeAlreadyExistsException() {
        super(HttpStatus.CONFLICT, DEFAULT_MESSAGE);
    }
}
