package com.timekeeper.bibexpo.inventory.exception;

import com.timekeeper.bibexpo.shared.error.ApiException;
import org.springframework.http.HttpStatus;

public class InventoryVariantAliasAlreadyExistsException extends ApiException {

    public static final String DEFAULT_MESSAGE = "This item already reads that spelling.";

    public InventoryVariantAliasAlreadyExistsException() {
        super(HttpStatus.CONFLICT, DEFAULT_MESSAGE);
    }
}
