package com.timekeeper.bibexpo.exception;

public class BibNotCollectedException extends RuntimeException {

    public BibNotCollectedException(String eventId, String bibNumber) {
        super(String.format("Bib number '%s' has not been collected yet for event '%s'", bibNumber, eventId));
    }

    public BibNotCollectedException(String message) {
        super(message);
    }
}
