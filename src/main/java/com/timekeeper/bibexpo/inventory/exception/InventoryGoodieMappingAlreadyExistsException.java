package com.timekeeper.bibexpo.inventory.exception;

import com.timekeeper.bibexpo.shared.error.ApiException;
import org.springframework.http.HttpStatus;

public class InventoryGoodieMappingAlreadyExistsException extends ApiException {

    public static final String DEFAULT_MESSAGE = "This goody is already linked to an item for this event.";

    public InventoryGoodieMappingAlreadyExistsException() {
        super(HttpStatus.CONFLICT, DEFAULT_MESSAGE);
    }
}
