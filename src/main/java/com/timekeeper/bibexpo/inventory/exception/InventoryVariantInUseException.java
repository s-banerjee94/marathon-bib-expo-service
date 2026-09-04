package com.timekeeper.bibexpo.inventory.exception;

import com.timekeeper.bibexpo.shared.error.ApiException;
import org.springframework.http.HttpStatus;

public class InventoryVariantInUseException extends ApiException {

    public static final String DEFAULT_MESSAGE =
            "This variant still has stock on hand and cannot be deleted.";

    public InventoryVariantInUseException() {
        super(HttpStatus.CONFLICT, DEFAULT_MESSAGE);
    }
}
