package com.timekeeper.bibexpo.participant.exception;

public class ChipNumberAlreadyExistsException extends RuntimeException {
    public ChipNumberAlreadyExistsException() {
        super("This chip number is already assigned to another participant.");
    }
}
