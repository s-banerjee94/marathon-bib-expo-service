package com.timekeeper.bibexpo.inventory.exception;

import com.timekeeper.bibexpo.shared.error.ApiException;
import org.springframework.http.HttpStatus;

public class InventoryGoodieMappingNotFoundException extends ApiException {

    public static final String DEFAULT_MESSAGE = "The goody link you requested does not exist.";

    public InventoryGoodieMappingNotFoundException() {
        super(HttpStatus.NOT_FOUND, DEFAULT_MESSAGE);
    }
}
