package com.timekeeper.bibexpo.messaging.provider.model.enums;

/**
 * HTTP verb a provider's send endpoint expects. GET carries everything in the query string;
 * POST may split values across the body and the query string.
 */
public enum HttpMethodType {
    GET,
    POST
}
