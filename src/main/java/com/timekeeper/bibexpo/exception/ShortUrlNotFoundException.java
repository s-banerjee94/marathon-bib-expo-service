package com.timekeeper.bibexpo.exception;

public class ShortUrlNotFoundException extends RuntimeException {

    public ShortUrlNotFoundException() {
        super("The verification link you used is invalid or has expired.");
    }
}
