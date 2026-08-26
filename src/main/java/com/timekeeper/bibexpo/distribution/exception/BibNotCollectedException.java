package com.timekeeper.bibexpo.distribution.exception;

public class BibNotCollectedException extends RuntimeException {

    public BibNotCollectedException() {
        super("This bib has not been collected yet.");
    }
}
