package com.timekeeper.bibexpo.whatsapp.exception;

public class WhatsAppTemplateScopeMismatchException extends RuntimeException {

    public WhatsAppTemplateScopeMismatchException() {
        super("This template is not registered under the WhatsApp account you are currently using.");
    }
}
