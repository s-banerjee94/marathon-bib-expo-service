package com.timekeeper.bibexpo.inventory.exception;

import com.timekeeper.bibexpo.shared.error.ApiException;
import org.springframework.http.HttpStatus;

public class InventoryVariantAlreadyExistsException extends ApiException {

    public static final String DEFAULT_MESSAGE = "A variant with these attribute values already exists for this item.";

    public InventoryVariantAlreadyExistsException() {
        super(HttpStatus.CONFLICT, DEFAULT_MESSAGE);
    }
}
