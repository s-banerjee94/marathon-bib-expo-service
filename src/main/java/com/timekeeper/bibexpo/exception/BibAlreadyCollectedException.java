package com.timekeeper.bibexpo.exception;

public class BibAlreadyCollectedException extends RuntimeException {

    public BibAlreadyCollectedException(String eventId, String bibNumber) {
        super(String.format("Bib number '%s' has already been collected for event '%s'", bibNumber, eventId));
    }

    public BibAlreadyCollectedException(String message) {
        super(message);
    }
}
