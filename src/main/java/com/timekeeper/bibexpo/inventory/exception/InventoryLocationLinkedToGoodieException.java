package com.timekeeper.bibexpo.inventory.exception;

import com.timekeeper.bibexpo.shared.error.ApiException;
import org.springframework.http.HttpStatus;

public class InventoryLocationLinkedToGoodieException extends ApiException {

    public static final String DEFAULT_MESSAGE =
            "You must hand the goodies using this location out from another one before deleting it.";

    public InventoryLocationLinkedToGoodieException() {
        super(HttpStatus.CONFLICT, DEFAULT_MESSAGE);
    }
}
