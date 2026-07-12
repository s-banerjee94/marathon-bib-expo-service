package com.timekeeper.bibexpo.demo.exception;

public class DemoSessionExpiredException extends RuntimeException {

    public DemoSessionExpiredException() {
        super("This demo QR code has expired, scan the fresh one on the screen.");
    }
}
