package com.timekeeper.bibexpo.inventory.exception;

import com.timekeeper.bibexpo.shared.error.ApiException;
import org.springframework.http.HttpStatus;

public class InventoryVariantAliasNotFoundException extends ApiException {

    public static final String DEFAULT_MESSAGE = "The spelling you requested does not exist.";

    public InventoryVariantAliasNotFoundException() {
        super(HttpStatus.NOT_FOUND, DEFAULT_MESSAGE);
    }
}
