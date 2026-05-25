package com.timekeeper.bibexpo.exception;

public class InvalidQrCodeException extends RuntimeException {

    public InvalidQrCodeException() {
        super("The QR code is invalid or does not belong to this event.");
    }
}
