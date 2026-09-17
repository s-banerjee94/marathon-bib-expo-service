package com.timekeeper.bibexpo.inventory.exception;

import com.timekeeper.bibexpo.shared.error.ApiException;
import org.springframework.http.HttpStatus;

public class InventoryItemInUseException extends ApiException {

    public static final String DEFAULT_MESSAGE =
            "You can delete this item only once its stock is back to zero at every location.";

    public InventoryItemInUseException() {
        super(HttpStatus.CONFLICT, DEFAULT_MESSAGE);
    }
}
