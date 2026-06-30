package com.timekeeper.bibexpo.ai.agent.exception;

/** Raised when a user has exhausted their daily AI token budget; surfaces to the client as HTTP 429. */
public class AiUsageLimitException extends RuntimeException {

    public AiUsageLimitException(String message) {
        super(message);
    }
}
