package com.timekeeper.bibexpo.whatsapp.exception;

public class WhatsAppConfigNotFoundException extends RuntimeException {

    public WhatsAppConfigNotFoundException() {
        super("You have not set up your own WhatsApp account yet.");
    }
}
