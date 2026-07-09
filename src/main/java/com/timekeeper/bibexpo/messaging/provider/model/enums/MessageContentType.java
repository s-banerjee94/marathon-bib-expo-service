package com.timekeeper.bibexpo.messaging.provider.model.enums;

/**
 * How the POST request body is encoded: a JSON object, or {@code application/x-www-form-urlencoded}
 * {@code key=value} pairs. Also decides how substituted token values are escaped inside the body.
 */
public enum MessageContentType {
    JSON,
    FORM
}
