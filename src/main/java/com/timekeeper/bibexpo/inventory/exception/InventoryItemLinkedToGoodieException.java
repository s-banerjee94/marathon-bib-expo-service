package com.timekeeper.bibexpo.inventory.exception;

import com.timekeeper.bibexpo.shared.error.ApiException;
import org.springframework.http.HttpStatus;

public class InventoryItemLinkedToGoodieException extends ApiException {

    public static final String DEFAULT_MESSAGE = "You must unlink this item from its event goodies before deleting it.";

    public InventoryItemLinkedToGoodieException() {
        super(HttpStatus.CONFLICT, DEFAULT_MESSAGE);
    }
}
