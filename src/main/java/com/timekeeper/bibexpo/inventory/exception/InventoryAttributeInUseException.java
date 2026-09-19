package com.timekeeper.bibexpo.inventory.exception;

import com.timekeeper.bibexpo.shared.error.ApiException;
import org.springframework.http.HttpStatus;

public class InventoryAttributeInUseException extends ApiException {

    public static final String DEFAULT_MESSAGE =
            "This attribute is still assigned to at least one item and cannot be deleted.";

    public InventoryAttributeInUseException() {
        super(HttpStatus.CONFLICT, DEFAULT_MESSAGE);
    }
}
