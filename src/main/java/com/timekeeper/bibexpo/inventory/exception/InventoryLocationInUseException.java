package com.timekeeper.bibexpo.inventory.exception;

import com.timekeeper.bibexpo.shared.error.ApiException;
import org.springframework.http.HttpStatus;

public class InventoryLocationInUseException extends ApiException {

    public static final String DEFAULT_MESSAGE =
            "This location already has stock history and cannot be deleted.";

    public InventoryLocationInUseException() {
        super(HttpStatus.CONFLICT, DEFAULT_MESSAGE);
    }
}
