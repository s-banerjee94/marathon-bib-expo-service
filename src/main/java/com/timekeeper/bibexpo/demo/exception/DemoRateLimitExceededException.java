package com.timekeeper.bibexpo.demo.exception;

public class DemoRateLimitExceededException extends RuntimeException {

    public DemoRateLimitExceededException() {
        super("You are doing that too fast, please wait a minute and try again.");
    }
}
