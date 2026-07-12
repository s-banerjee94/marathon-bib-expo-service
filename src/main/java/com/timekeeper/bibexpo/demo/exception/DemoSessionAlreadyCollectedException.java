package com.timekeeper.bibexpo.demo.exception;

public class DemoSessionAlreadyCollectedException extends RuntimeException {

    public DemoSessionAlreadyCollectedException() {
        super("This bib has already been collected, scan the fresh QR code on the screen.");
    }
}
