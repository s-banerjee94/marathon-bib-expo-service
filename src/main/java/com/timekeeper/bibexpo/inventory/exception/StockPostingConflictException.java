package com.timekeeper.bibexpo.inventory.exception;

import com.timekeeper.bibexpo.inventory.model.dto.response.StockPostingConflictResponse;
import lombok.Getter;

import java.util.List;

/**
 * Lines of a posting the stock could not take. Nothing of the posting is written, and the controller
 * answers 409 with every failed line so the screen can mark them.
 */
@Getter
public class StockPostingConflictException extends RuntimeException {

    public static final String COUNT_CHANGED =
            "The stock changed while you were counting, so count the marked lines again.";
    public static final String NOT_ENOUGH = "There is not enough stock for the marked lines.";

    private final List<StockPostingConflictResponse.Line> lines;

    public StockPostingConflictException(List<StockPostingConflictResponse.Line> lines) {
        super(lines.stream().anyMatch(l -> l.getExpectedOnHand() != null) ? COUNT_CHANGED : NOT_ENOUGH);
        this.lines = lines;
    }
}
