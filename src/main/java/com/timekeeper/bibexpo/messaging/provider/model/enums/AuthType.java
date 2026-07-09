package com.timekeeper.bibexpo.messaging.provider.model.enums;

/**
 * How the provider authenticates a request. Exactly one applies per provider:
 * {@code TOKEN} uses the API key, {@code USERNAME_PASSWORD} uses the login pair.
 */
public enum AuthType {
    TOKEN,
    USERNAME_PASSWORD
}
