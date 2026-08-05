package com.timekeeper.bibexpo.distribution.exception;

public class BibAlreadyCollectedException extends RuntimeException {

    public BibAlreadyCollectedException() {
        super("This bib has already been collected.");
    }
}
