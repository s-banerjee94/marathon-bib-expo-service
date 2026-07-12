package com.timekeeper.bibexpo.demo.exception;

public class DemoSessionNotFoundException extends RuntimeException {

    public DemoSessionNotFoundException() {
        super("This demo QR code is not valid.");
    }
}
