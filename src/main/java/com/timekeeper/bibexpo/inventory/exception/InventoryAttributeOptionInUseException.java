package com.timekeeper.bibexpo.inventory.exception;

import com.timekeeper.bibexpo.shared.error.ApiException;
import org.springframework.http.HttpStatus;

public class InventoryAttributeOptionInUseException extends ApiException {

    public static final String DEFAULT_MESSAGE =
            "This value is still assigned to at least one item or variant and cannot be deleted.";

    public InventoryAttributeOptionInUseException() {
        super(HttpStatus.CONFLICT, DEFAULT_MESSAGE);
    }
}
