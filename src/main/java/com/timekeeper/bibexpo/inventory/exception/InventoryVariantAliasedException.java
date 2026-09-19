package com.timekeeper.bibexpo.inventory.exception;

import com.timekeeper.bibexpo.shared.error.ApiException;
import org.springframework.http.HttpStatus;

public class InventoryVariantAliasedException extends ApiException {

    public static final String DEFAULT_MESSAGE = "You must remove this variant from the item's spellings before deleting it.";

    public InventoryVariantAliasedException() {
        super(HttpStatus.CONFLICT, DEFAULT_MESSAGE);
    }
}
