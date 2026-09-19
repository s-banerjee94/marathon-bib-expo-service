package com.timekeeper.bibexpo.inventory.exception;

import com.timekeeper.bibexpo.shared.error.ApiException;
import org.springframework.http.HttpStatus;

public class InventoryAttributeOptionNotFoundException extends ApiException {

    public static final String DEFAULT_MESSAGE = "The attribute value you requested does not exist.";

    public InventoryAttributeOptionNotFoundException() {
        super(HttpStatus.NOT_FOUND, DEFAULT_MESSAGE);
    }
}
