package com.timekeeper.bibexpo.inventory.exception;

import com.timekeeper.bibexpo.shared.error.ApiException;
import org.springframework.http.HttpStatus;

public class InventoryItemInUseException extends ApiException {

    public static final String DEFAULT_MESSAGE =
            "This item still has stock on hand and cannot be deleted.";

    public InventoryItemInUseException() {
        super(HttpStatus.CONFLICT, DEFAULT_MESSAGE);
    }
}
