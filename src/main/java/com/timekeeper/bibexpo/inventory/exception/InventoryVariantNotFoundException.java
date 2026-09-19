package com.timekeeper.bibexpo.inventory.exception;

import com.timekeeper.bibexpo.shared.error.ApiException;
import org.springframework.http.HttpStatus;

public class InventoryVariantNotFoundException extends ApiException {

    public static final String DEFAULT_MESSAGE = "The item variant you requested does not exist.";

    public InventoryVariantNotFoundException() {
        super(HttpStatus.NOT_FOUND, DEFAULT_MESSAGE);
    }
}
