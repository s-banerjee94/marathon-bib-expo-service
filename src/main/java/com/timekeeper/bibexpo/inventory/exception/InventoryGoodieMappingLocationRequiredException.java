package com.timekeeper.bibexpo.inventory.exception;

import com.timekeeper.bibexpo.shared.error.ApiException;
import org.springframework.http.HttpStatus;

public class InventoryGoodieMappingLocationRequiredException extends ApiException {

    public static final String DEFAULT_MESSAGE =
            "You must choose the location this goody is handed out from, because the event is already published.";

    public InventoryGoodieMappingLocationRequiredException() {
        super(HttpStatus.CONFLICT, DEFAULT_MESSAGE);
    }
}
