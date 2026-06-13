package com.timekeeper.bibexpo.messaging.provider.model.enums;

/**
 * Where a single request parameter is placed when the call is built. The request body is built
 * separately from the provider's body template, so only headers and query string are listed here.
 */
public enum ParamLocation {
    HEADER,
    QUERY
}
