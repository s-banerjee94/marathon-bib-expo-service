package com.timekeeper.bibexpo.inventory.exception;

import com.timekeeper.bibexpo.shared.error.ApiException;
import org.springframework.http.HttpStatus;

public class StockPostingNotReversibleException extends ApiException {

    public static final String ALREADY_REVERSED = "This stock change has already been undone.";
    public static final String IS_REVERSAL = "You cannot undo an undo, so post the change again instead.";

    public StockPostingNotReversibleException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
