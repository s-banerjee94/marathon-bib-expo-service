package com.timekeeper.bibexpo.messaging.provider.model.enums;

/**
 * HTTP verb a provider's send endpoint expects. GET carries everything in the query string;
 * the body-carrying verbs may split values across the body and the query string.
 */
public enum HttpMethodType {
    GET,
    POST,
    PUT,
    PATCH
}
