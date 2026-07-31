package com.timekeeper.bibexpo.messaging.provider.model.enums;

/**
 * How the request body is encoded, and therefore how a substituted token value is escaped inside it:
 * a JSON object, {@code application/x-www-form-urlencoded} pairs, an XML document, or plain text.
 */
public enum MessageContentType {
    JSON,
    FORM,
    XML,
    TEXT
}
