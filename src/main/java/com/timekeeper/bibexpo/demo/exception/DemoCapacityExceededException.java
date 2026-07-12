package com.timekeeper.bibexpo.demo.exception;

public class DemoCapacityExceededException extends RuntimeException {

    public DemoCapacityExceededException() {
        super("The live demo is busy right now, please try again in a few minutes.");
    }
}
