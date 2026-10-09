package com.timekeeper.bibexpo.inventory.exception;

import com.timekeeper.bibexpo.shared.error.ApiException;
import org.springframework.http.HttpStatus;

public class InventoryAttributeAlreadyExistsException extends ApiException {

    public static final String DEFAULT_MESSAGE = "Your organization already has an attribute with this name.";

    public InventoryAttributeAlreadyExistsException() {
        super(HttpStatus.CONFLICT, DEFAULT_MESSAGE);
    }
}
