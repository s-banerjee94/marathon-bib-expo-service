package com.timekeeper.bibexpo.participant.exception;

public class BibNumberAlreadyExistsException extends RuntimeException {
    public BibNumberAlreadyExistsException() {
        super("A participant with this BIB number already exists.");
    }
}
