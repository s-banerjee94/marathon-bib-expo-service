package com.timekeeper.bibexpo.ai.agent.exception;

/**
 * Thrown when the Python AI agent reports it is rate-limited by the upstream model (HTTP 429),
 * i.e. too many tokens in the current minute. Surfaced to the caller as a 429 so a transient
 * throttle reads as "busy, try again" rather than an outage.
 */
public class AiAgentBusyException extends RuntimeException {

    public AiAgentBusyException(String message) {
        super(message);
    }
}
