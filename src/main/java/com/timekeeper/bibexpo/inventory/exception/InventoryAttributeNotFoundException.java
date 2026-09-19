package com.timekeeper.bibexpo.inventory.exception;

import com.timekeeper.bibexpo.shared.error.ApiException;
import org.springframework.http.HttpStatus;

public class InventoryAttributeNotFoundException extends ApiException {

    public static final String DEFAULT_MESSAGE = "The attribute you requested does not exist.";

    public InventoryAttributeNotFoundException() {
        super(HttpStatus.NOT_FOUND, DEFAULT_MESSAGE);
    }
}
