package com.timekeeper.bibexpo.inventory.exception;

import com.timekeeper.bibexpo.shared.error.ApiException;
import org.springframework.http.HttpStatus;

public class InventoryTermInUseException extends ApiException {

    public static final String DEFAULT_MESSAGE =
            "This term is still assigned to at least one item or location and cannot be deleted.";

    public InventoryTermInUseException() {
        super(HttpStatus.CONFLICT, DEFAULT_MESSAGE);
    }
}
