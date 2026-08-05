package com.timekeeper.bibexpo.distribution.exception;

public class GoodiesItemNotFoundException extends RuntimeException {

    public GoodiesItemNotFoundException() {
        super("The goodies item you requested does not exist.");
    }
}
